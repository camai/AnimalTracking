package com.animaltracking.domain.usecase

import com.animaltracking.domain.model.BoundingBox
import com.animaltracking.domain.model.TrackedObject
import com.animaltracking.domain.repository.ObjectTracker
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class IoUTracker @Inject constructor() : ObjectTracker {

    private var nextObjectId = 0
    private var trackedObjects = mutableListOf<TrackedObject>()
    private val iouThreshold = 0.15f // 매칭 문턱값 완화 (0.3 -> 0.15)하여 트래킹 끊김 방지

    private val maxFrameMiss = 30 // Stickiness 조절: 40 -> 30 (약 1초)
    private var lockId: Int? = null

    // Simple tracking state: ID -> (MissCount, LastSeenTrackedObject)
    private val tracks = HashMap<Int, Pair<Int, TrackedObject>>()

    override fun setLockId(id: Int?) {
        this.lockId = id
    }

    override fun getLockId(): Int? {
        return this.lockId
    }

    override fun track(detections: List<BoundingBox>): List<TrackedObject> {
        val newTrackedObjects = ArrayList<TrackedObject>()
        
        // 1. Try to match currently tracked objects with new detections
        val activeTracks = tracks.map { it.value.second }.toMutableList()
        val unmatchedDetections = detections.toMutableList()
        
        // Match existing tracks to new detections
        val matchedIds = HashSet<Int>()
        val matches = ArrayList<Pair<TrackedObject, BoundingBox>>()
        
        for (track in activeTracks) {
            var bestDetection: BoundingBox? = null
            var maxIoU = -1f
            
            for (detection in unmatchedDetections) {
                val iou = calculateIoU(track.boundingBox, detection)
                if (iou > iouThreshold && iou > maxIoU) {
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
        
        // Update matched tracks
        for ((oldTrack, detection) in matches) {
            val smoothedBox = smoothBox(oldTrack.boundingBox, detection, 0.7f)
            val updatedTrack = TrackedObject(oldTrack.id, smoothedBox)
            tracks[oldTrack.id] = 0 to updatedTrack // Reset miss count
            newTrackedObjects.add(updatedTrack)
        }
        
        // Handle Unmatched Tracks (Disappeared?)
        val disappearedIds = tracks.keys - matchedIds
        for (id in disappearedIds) {
            val (missCount, lastTrack) = tracks[id]!!
            if (missCount < maxFrameMiss) {
                tracks[id] = (missCount + 1) to lastTrack
                
                // 예측 함수: 마지막 위치 유지 (Static)
                val predictedTrack = if (missCount > 0) {
                    val prediction = predictNextPosition(lastTrack, missCount)
                    TrackedObject(lastTrack.id, prediction)
                } else {
                    lastTrack
                }
                
                newTrackedObjects.add(predictedTrack)
            } else {
                tracks.remove(id)
                // 만약 락 걸린 객체가 사라지면 락 해제
                if (lockId == id) {
                    lockId = null
                }
            }
        }
        
        // Handle Unmatched Detections (New Objects)
        if (tracks.isEmpty() && unmatchedDetections.isNotEmpty()) {
            // Initial Frame Logic: Single Target Selection
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
        
        // Auto-Lock Logic:
        // 만약 lockId가 없다면, 가장 좋은 후보를 선정 (Center Priority + Size + Confidence)
        if (lockId == null && newTrackedObjects.isNotEmpty()) {
            val bestCandidate = newTrackedObjects.maxByOrNull { obj ->
                val box = obj.boundingBox
                val areaScore = box.w * box.h
                val confScore = box.cnf
                // Center Score: 1.0 at center(0.5), 0.0 at edges
                val centerScore = 1.0f - kotlin.math.abs(box.cx - 0.5f) * 2
                
                // 가중치 합산: 신뢰도 > 중심 > 크기
                // Confidence가 제일 중요. 그 다음 중심.
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
            // 방어 코드: 만약 락이 풀렸는데 객체가 있다면 다시 다음 프레임에 잡힐 것임.
            // 일단은 빈 리스트 대신 전체 리스트 반환 (Auto-Lock이 즉시 동작하므로 이 분기는 거의 안 탐)
            newTrackedObjects
        }
    }

    override fun reset() {
        tracks.clear()
        nextObjectId = 0
        lockId = null
    }
    
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
    
    // 예측 함수: 랜덤 떨림 제거하고 마지막 위치 유지 (Static)
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
            cnf = newBox.cnf, // 신뢰도는 최신 값 사용
            cls = newBox.cls,
            clsName = newBox.clsName
        )
    }
}
