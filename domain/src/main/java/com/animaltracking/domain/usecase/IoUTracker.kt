package com.animaltracking.domain.usecase

import com.animaltracking.domain.model.BoundingBox
import com.animaltracking.domain.model.TrackedObject
import com.animaltracking.domain.repository.ObjectTracker
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class IoUTracker @Inject constructor() : ObjectTracker {

    private var nextObjectId = 0
    private val iouThreshold = 0.15f
    private val lockedCenterDistanceBase = 0.25f
    private val lockedCenterDistanceMax = 0.6f
    private val lockedAreaChangeThreshold = 0.7f
    private val lockedScoreMin = 0.25f
    private val lockedScoreMargin = 0.05f

    private val maxFrameMiss = 30 // 유지력 조절: 40 -> 30 (약 1초)
    private val lockedMaxFrameMiss = 90
    private var lockId: Int? = null

    private val tracks = HashMap<Int, Pair<Int, TrackedObject>>()

    override fun setLockId(id: Int?) {
        this.lockId = id
    }

    override fun getLockId(): Int? {
        return this.lockId
    }

    override fun track(detections: List<BoundingBox>): List<TrackedObject> {
        if (lockId != null) {
            return trackLocked(detections)
        }

        val newTrackedObjects = ArrayList<TrackedObject>()
        
        // 1. 현재 트랙과 신규 탐지를 매칭
        val activeTracks = tracks.map { it.value.second }.toMutableList()
        val unmatchedDetections = detections.toMutableList()
        
        // 기존 트랙을 신규 탐지와 매칭
        val matchedIds = HashSet<Int>()
        val matches = ArrayList<Pair<TrackedObject, BoundingBox>>()
        
        for (track in activeTracks) {
            var bestDetection: BoundingBox? = null
            var maxIoU = -1f
            
            val threshold = iouThreshold
            for (detection in unmatchedDetections) {
                val iou = calculateIoU(track.boundingBox, detection)
                if (iou > threshold && iou > maxIoU) {
                    maxIoU = iou
                    bestDetection = detection
                }
            }
            
            if (bestDetection != null) {
                matches.add(track to bestDetection)
                unmatchedDetections.remove(bestDetection)
                matchedIds.add(track.id)
            }
        }
        
        // 매칭된 트랙 갱신
        for ((oldTrack, detection) in matches) {
            val smoothedBox = smoothBox(oldTrack.boundingBox, detection, 0.7f)
            val updatedTrack = TrackedObject(oldTrack.id, smoothedBox)
            tracks[oldTrack.id] = 0 to updatedTrack // 미스 카운트 초기화
            newTrackedObjects.add(updatedTrack)
        }
        
        // 매칭되지 않은 트랙 처리 (사라짐?)
        val disappearedIds = tracks.keys - matchedIds
        for (id in disappearedIds) {
            val (missCount, lastTrack) = tracks[id]!!
            val isLocked = id == lockId
            val maxMiss = if (isLocked) lockedMaxFrameMiss else maxFrameMiss
            if (missCount < maxMiss) {
                tracks[id] = (missCount + 1) to lastTrack
                
                // 마지막 위치 유지 (정지)
                val predictedTrack = if (missCount > 0) {
                    val prediction = predictNextPosition(lastTrack, missCount)
                    TrackedObject(lastTrack.id, prediction)
                } else {
                    lastTrack
                }
                
                newTrackedObjects.add(predictedTrack)
            } else if (isLocked) {
                tracks[id] = maxMiss to lastTrack
            } else {
                tracks.remove(id)
            }
        }
        
        // 매칭되지 않은 탐지 처리 (새 객체)
        if (tracks.isEmpty() && unmatchedDetections.isNotEmpty()) {
            // 초기 프레임 로직: 단일 대상 선택
            val bestDetection = unmatchedDetections.maxByOrNull { it.cnf }
            if (bestDetection != null) {
                val newId = nextObjectId++
                val newTrack = TrackedObject(newId, bestDetection)
                tracks[newId] = 0 to newTrack
                newTrackedObjects.add(newTrack)
            }
        } else {
            for (detection in unmatchedDetections) {
                val newId = nextObjectId++
                val newTrack = TrackedObject(newId, detection)
                tracks[newId] = 0 to newTrack
                newTrackedObjects.add(newTrack)
            }
        }
        
        // 자동 락 로직:
        // lockId가 없으면, 가장 좋은 후보를 선정 (중심 우선 + 크기 + 신뢰도)
        if (lockId == null && newTrackedObjects.isNotEmpty()) {
            val bestCandidate = newTrackedObjects.maxByOrNull { obj ->
                val box = obj.boundingBox
                val areaScore = box.w * box.h
                val confScore = box.cnf
                // 중심 점수: 중심(0.5)은 1.0, 가장자리는 0.0
                val centerScore = 1.0f - kotlin.math.abs(box.cx - 0.5f) * 2
                
                // 가중치 합산: 신뢰도 > 중심 > 크기
                // 신뢰도가 가장 중요하고, 그다음은 중심.
                confScore * 2.0f + centerScore * 1.5f + areaScore
            }
            
            if (bestCandidate != null) {
                // 노이즈 방지: 최소한의 신뢰도(0.6)는 넘어야 락
                if (bestCandidate.boundingBox.cnf > 0.6f) {
                    lockId = bestCandidate.id
                }
            }
        }

        return if (lockId != null) {
            newTrackedObjects.filter { it.id == lockId }
        } else {
            // 만약 락이 풀렸는데 객체가 있다면 다시 다음 프레임에 잡힐 것임.
            // 일단은 빈 리스트 대신 전체 리스트 반환 (자동 락이 즉시 동작하므로 이 분기는 거의 안 탐)
            newTrackedObjects
        }
    }

    override fun reset() {
        tracks.clear()
        nextObjectId = 0
        lockId = null
    }

    private fun trackLocked(detections: List<BoundingBox>): List<TrackedObject> {
        val lockedId = lockId ?: return emptyList()
        val lockedEntry = tracks[lockedId]
        val lockedTrack = lockedEntry?.second
        val missCount = lockedEntry?.first ?: 0

        if (lockedTrack == null) {
            val bestDetection = detections.maxByOrNull { it.cnf }
            return if (bestDetection != null) {
                val newTrack = TrackedObject(lockedId, bestDetection)
                tracks[lockedId] = 0 to newTrack
                listOf(newTrack)
            } else {
                emptyList()
            }
        }

        val match = selectLockedMatch(lockedTrack, detections, missCount)
        val hasCandidate = match.detection != null
        val isAmbiguous = hasCandidate &&
            match.secondScore >= 0f &&
            (match.bestScore - match.secondScore) < lockedScoreMargin
        val isStrongEnough = hasCandidate && match.bestScore >= lockedScoreMin

        return if (isStrongEnough && !isAmbiguous) {
            val smoothedBox = smoothBox(lockedTrack.boundingBox, match.detection!!, 0.7f)
            val updatedTrack = TrackedObject(lockedTrack.id, smoothedBox)
            tracks[lockedId] = 0 to updatedTrack
            listOf(updatedTrack)
        } else {
            val nextMiss = kotlin.math.min(missCount + 1, lockedMaxFrameMiss)
            tracks[lockedId] = nextMiss to lockedTrack
            val predictedTrack = if (nextMiss > 0) {
                val prediction = predictNextPosition(lockedTrack, nextMiss)
                TrackedObject(lockedTrack.id, prediction)
            } else {
                lockedTrack
            }
            listOf(predictedTrack)
        }
    }

    private fun selectLockedMatch(
        lockedTrack: TrackedObject,
        detections: List<BoundingBox>,
        missCount: Int
    ): LockedMatch {
        var bestDetection: BoundingBox? = null
        var bestScore = -1f
        var secondScore = -1f

        val dynamicCenterThreshold = (lockedCenterDistanceBase + missCount * 0.01f)
            .coerceAtMost(lockedCenterDistanceMax)

        for (detection in detections) {
            val iou = calculateIoU(lockedTrack.boundingBox, detection)
            val centerDistance = calculateCenterDistance(lockedTrack.boundingBox, detection)
            val areaChange = calculateRelativeAreaChange(lockedTrack.boundingBox, detection)

            val centerScore = 1f - (centerDistance / dynamicCenterThreshold).coerceAtMost(1f)
            val areaScore = 1f - (areaChange / lockedAreaChangeThreshold).coerceAtMost(1f)
            val score = iou * 0.6f + centerScore * 0.3f + areaScore * 0.1f

            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                bestDetection = detection
            } else if (score > secondScore) {
                secondScore = score
            }
        }

        return LockedMatch(bestDetection, bestScore, secondScore)
    }

    private data class LockedMatch(
        val detection: BoundingBox?,
        val bestScore: Float,
        val secondScore: Float
    )
    
    private fun calculateIoU(boxA: BoundingBox, boxB: BoundingBox): Float {
        val xA = max(boxA.x1, boxB.x1)
        val yA = max(boxA.y1, boxB.y1)
        val xB = min(boxA.x2, boxB.x2)
        val yB = min(boxA.y2, boxB.y2)

        val interArea = max(0f, xB - xA) * max(0f, yB - yA)
        val boxAArea = (boxA.x2 - boxA.x1) * (boxA.y2 - boxA.y1)
        val boxBArea = (boxB.x2 - boxB.x1) * (boxB.y2 - boxB.y1)

        return interArea / (boxAArea + boxBArea - interArea)
    }

    private fun calculateCenterDistance(boxA: BoundingBox, boxB: BoundingBox): Float {
        val dx = boxA.cx - boxB.cx
        val dy = boxA.cy - boxB.cy
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun calculateRelativeAreaChange(boxA: BoundingBox, boxB: BoundingBox): Float {
        val areaA = boxA.w * boxA.h
        val areaB = boxB.w * boxB.h
        if (areaA == 0f) return 1f
        return kotlin.math.abs(areaA - areaB) / areaA
    }
    
    // 떨림 제거하고 마지막 위치 유지 (정지)
    private fun predictNextPosition(track: TrackedObject, missFrames: Int): BoundingBox {
        return track.boundingBox
    }

    private fun smoothBox(oldBox: BoundingBox, newBox: BoundingBox, alpha: Float): BoundingBox {
        return BoundingBox(
            x1 = oldBox.x1 * (1 - alpha) + newBox.x1 * alpha,
            y1 = oldBox.y1 * (1 - alpha) + newBox.y1 * alpha,
            x2 = oldBox.x2 * (1 - alpha) + newBox.x2 * alpha,
            y2 = oldBox.y2 * (1 - alpha) + newBox.y2 * alpha,
            cx = oldBox.cx * (1 - alpha) + newBox.cx * alpha,
            cy = oldBox.cy * (1 - alpha) + newBox.cy * alpha,
            w = oldBox.w * (1 - alpha) + newBox.w * alpha,
            h = oldBox.h * (1 - alpha) + newBox.h * alpha,
            cnf = newBox.cnf,
            cls = newBox.cls,
            clsName = newBox.clsName
        )
    }
}
