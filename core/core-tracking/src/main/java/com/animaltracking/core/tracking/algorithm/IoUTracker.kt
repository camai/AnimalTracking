package com.animaltracking.core.tracking.algorithm

import android.util.Log
import com.animaltracking.core.tracking.model.BoundingBox
import com.animaltracking.core.tracking.model.TrackedObject
import com.animaltracking.core.tracking.repository.ObjectTracker
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.pow

class IoUTracker @Inject constructor() : ObjectTracker {

    private var nextObjectId = 0
    private val iouThreshold = 0.15f // 기본 IoU 임계값
    // 락 상태 기준값(엄격한 기본값)
    private val lockedCenterDistanceBase = 0.3f  // 중심 거리 기본값
    private val lockedCenterDistanceMax = 0.75f  // 중심 거리 최대값
    private val lockedMinIouBase = 0.10f         // 최소 IoU 기본값(분산 완화)
    private val lockedMinIouMin = 0.01f          // 최소 IoU 하한
    private val lockedMultiCandidateMinIoU = 0.05f
    private val lockedDesperateConfidence = 0.45f
    private val lockedDesperateCenterScore = 0.45f
    private val lockedAreaChangeThreshold = 1.3f // 상대 면적 변화 허용치(1.3 = 30% 차이)
    private val lockedAreaChangeMax = 2.0f       // 면적 변화 최대치
    private val lockedScoreMin = 0.25f           // 더 엄격한 매칭 점수
    private val lockedScoreMargin = 0.12f

    private val maxFrameMiss = 15 // 단기 미스 허용(빠른 복구)
    private val lockedMaxFrameMiss = 80 // 락 유지 시간 확대

    // 안전 제한값(예측 드리프트 방지)
    private val maxVelocity = 0.02f              // 프레임당 최대 이동 제한
    private var lockId: Int? = null
    
    // 자동 락 쿨다운(타임아웃 직후 스위칭 방지)
    private var lastUnlockTime = 0L
    private val autoLockCooldownMs = 1000L // [수정] 재락 속도 개선
    
    // 언락 사유 기록(쿨다운 가중치 적용)
    private var lastUnlockReason: String? = null

    // 트래킹 상태: ID -> (미스 카운트, 마지막 트랙)
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

        // 1. 활성 트랙 매칭
        val activeTracks = tracks.map { it.value.second }.toMutableList()
        val unmatchedDetections = detections.toMutableList()
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
            }
        }

        // 매칭 트랙 업데이트
        for ((oldTrack, detection) in matches) {
            val updatedTrack = createUpdatedTrack(
                id = oldTrack.id, 
                smoothingBaseBox = oldTrack.boundingBox, // 기존 박스 기준 스무딩
                velocityRefBox = oldTrack.boundingBox,   // 속도 기준 박스
                prevVx = oldTrack.vx, // 속도 스무딩용 이전 값
                prevVy = oldTrack.vy,
                newBox = detection, 
                alpha = 0.7f
            )
            tracks[oldTrack.id] = 0 to updatedTrack
            newTrackedObjects.add(updatedTrack)
        }

        // 사라진 트랙 처리
        val matchedIds = matches.map { it.first.id }.toSet()
        val disappearedIds = tracks.keys - matchedIds

        for (id in disappearedIds) {
            val (missCount, lastTrack) = tracks[id]!!
            if (missCount < maxFrameMiss) {
                tracks[id] = (missCount + 1) to lastTrack
                val predictedTrack = TrackedObject(lastTrack.id, predictNextPosition(lastTrack, missCount))
                newTrackedObjects.add(predictedTrack)
            } else {
                tracks.remove(id)
            }
        }

        // 신규 객체 처리(단일 말 집중)
        if (tracks.isEmpty() && unmatchedDetections.isNotEmpty()) {
             // 초기 프레임: 하나만 선택
             val bestDetection = unmatchedDetections.maxByOrNull { it.cnf }
             if (bestDetection != null && bestDetection.cnf > 0.6f) {
                 val newId = nextObjectId++
                 val newTrack = TrackedObject(newId, bestDetection)
                 tracks[newId] = 0 to newTrack
                 newTrackedObjects.add(newTrack)
                 Log.d("[IoUTracker]","Initial Track: Created ID=$newId with confidence=${bestDetection.cnf}")
             }
        } else {
              // 노이즈 방지: 높은 신뢰도만 추가
             for (detection in unmatchedDetections) {
                 if (detection.cnf > 0.75f) { // 더 높은 임계값 적용
                     val newId = nextObjectId++
                     val newTrack = TrackedObject(newId, detection)
                     tracks[newId] = 0 to newTrack
                     newTrackedObjects.add(newTrack)
                     Log.d("[IoUTracker]","New Track: Added ID=$newId with confidence=${detection.cnf}")
                 }
             }
        }

        // 자동 락 로직(쿨다운 포함)
        if (lockId == null && newTrackedObjects.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            val timeSinceUnlock = currentTime - lastUnlockTime
            
            // 쿨다운 유지 여부 확인
            val cooldownActive = timeSinceUnlock < autoLockCooldownMs
            val isTimeoutUnlock = lastUnlockReason == "Timeout"
            
            // 타임아웃 언락은 더 엄격한 쿨다운 적용
            val effectiveCooldown = if (isTimeoutUnlock) autoLockCooldownMs * 2 else autoLockCooldownMs
            val shouldSkipAutoLock = timeSinceUnlock < effectiveCooldown
            
            if (!shouldSkipAutoLock) {
                val bestCandidate = newTrackedObjects.maxByOrNull { obj ->
                    val box = obj.boundingBox
                    val areaScore = box.w * box.h
                    val confScore = box.cnf
                    val centerScore = 1.0f - kotlin.math.abs(box.cx - 0.5f) * 2
                    confScore * 2.0f + centerScore * 1.5f + areaScore
                }

                // 타임아웃 이후 더 엄격한 자동 락 조건
                val requiredConfidence = if (isTimeoutUnlock) 0.9f else 0.8f
                if (bestCandidate != null && bestCandidate.boundingBox.cnf > requiredConfidence) {
                    lockId = bestCandidate.id
                    Log.d("[IoUTracker]","Auto-Lock: Locked onto horse ID=${bestCandidate.id} with confidence=${bestCandidate.boundingBox.cnf} (after ${timeSinceUnlock}ms cooldown)")
                }
            } else {
                Log.d("[IoUTracker]", "Auto-Lock: Skipping due to cooldown (${timeSinceUnlock}ms < ${effectiveCooldown}ms, reason=${lastUnlockReason})")
            }
        }

        return if (lockId != null) {
            newTrackedObjects.filter { it.id == lockId }
        } else {
            newTrackedObjects
        }
    }

    override fun reset() {
        tracks.clear()
        nextObjectId = 0
        lockId = null
        lastUnlockTime = 0L
        lastUnlockReason = null
    }

    private fun trackLocked(detections: List<BoundingBox>): List<TrackedObject> {
        val lockedId = lockId ?: return emptyList()
        val lockedEntry = tracks[lockedId]
        val lockedTrack = lockedEntry?.second
        val missCount = lockedEntry?.first ?: 0

        if (lockedTrack != null) {
            Log.d("[IoUTracker]", "Frame Start. ID=$lockedId Miss=$missCount VX=${lockedTrack.vx} VY=${lockedTrack.vy}")
        }

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

        // 1. 현재 프레임에서 객체가 위치해야 할 곳을 예측
        // 이는 예상되는 미래 위치와 매칭함으로써 "지연(lag)" 문제를 해결하여,
        // 사실상 딜레이를 상쇄하는 효과를 줍니다.
        val predictedBox = predictNextPosition(lockedTrack, missCount + 1)

        // 2. 예측 위치와 매칭 수행
        val match = selectLockedMatch(predictedBox, detections, missCount)
        val hasCandidate = match.detection != null
        val isAmbiguous = hasCandidate &&
            match.secondScore >= 0f &&
            (match.bestScore - match.secondScore) < lockedScoreMargin
        val isStrongEnough = hasCandidate && match.bestScore >= lockedScoreMin

        return if (isStrongEnough && !isAmbiguous) {
            Log.d("[IoUTracker]", "MATCH FOUND! Score=${match.bestScore}")
            // 3. 예측값을 기준(baseline)으로 스무딩 업데이트 수행
            // 이렇게 하면 이전 위치로 "되돌아가는(snapping back)" 현상을 방지합니다.
            val updatedTrack = createUpdatedTrack(
                id = lockedTrack.id, 
                smoothingBaseBox = predictedBox,       // 락 상태: 예측값 기준 스무딩 (지연 없음)
                velocityRefBox = lockedTrack.boundingBox, // 락 상태: 이전 위치 기준 속도 계산 (정확한 변위)
                prevVx = lockedTrack.vx, // 스무딩용
                prevVy = lockedTrack.vy,
                newBox = match.detection!!, 
                alpha = 0.7f
            )
            tracks[lockedId] = 0 to updatedTrack
            listOf(updatedTrack)
        } else {
            val nextMiss = missCount + 1
            Log.d("[IoUTracker]", "MISSING... Count=$nextMiss, availableDetections=${detections.size}")
            if (detections.isNotEmpty()) {
                Log.d("[IoUTracker]", "Available detection confidences: [${detections.joinToString { "%.2f".format(it.cnf) }}]")
            } 
            
            // 지속적인 단일 말 추적을 위한 더 보수적인 멈춤(Stuck) 감지
            val speed = kotlin.math.sqrt(lockedTrack.vx * lockedTrack.vx + lockedTrack.vy * lockedTrack.vy)
            val hasDetections = detections.isNotEmpty()
            val isStuck = nextMiss > 30 && speed < 0.0006f && !hasDetections
            
            // 드리프트 방지: 예측 위치가 마지막으로 알려진 위치에서 너무 멀어졌는지 확인
            val currentPrediction = predictNextPosition(lockedTrack, nextMiss)
            val driftDistance = calculateCenterDistance(lockedTrack.boundingBox, currentPrediction)
            val isDrifted = driftDistance > 0.35f && !hasDetections
            
            // 겹침 감지: 여러 말이 매우 근접해 있는 상황(겹침)인지 확인
            val hasOverlapSwitch = detectOverlapSwitchNeeded(currentPrediction, detections, nextMiss)
            
            val isLostTooLong = nextMiss >= lockedMaxFrameMiss

            if (isLostTooLong || isStuck || isDrifted || hasOverlapSwitch) {
                // 너무 오래 놓쳤거나, 제자리에 멈췄거나, 너무 멀리 드리프트했거나, 겹침 전환이 필요한 경우 언락
                val reason = when {
                    hasOverlapSwitch -> "OverlapSwitch"
                    isDrifted -> "Drift(Dist=${"%.4f".format(driftDistance)})"
                    isStuck -> "Stuck(Speed=${"%.5f".format(speed)})"
                    else -> "Timeout"
                }
                Log.d("[IoUTracker]", "Drop Lock. Reason=$reason")
                
                // 쿨다운 로직을 위해 언락 시간과 사유 기록
                lastUnlockTime = System.currentTimeMillis()
                lastUnlockReason = reason
                
                lockId = null
                tracks.remove(lockedId)
                emptyList()
            } else {
                tracks[lockedId] = nextMiss to lockedTrack
                // 이미 계산된 예측값 사용
                val predictedTrack = TrackedObject(lockedTrack.id, predictNextPosition(lockedTrack, nextMiss))
                listOf(predictedTrack)
            }
        }
    }

    private fun selectLockedMatch(
        predictedBox: BoundingBox, 
        detections: List<BoundingBox>,
        missCount: Int
    ): LockedMatch {
        var bestDetection: BoundingBox? = null
        var bestScore = -1f
        var secondScore = -1f

        val dynamicCenterThreshold = (lockedCenterDistanceBase + missCount * 0.02f)
            .coerceAtMost(lockedCenterDistanceMax)
        val dynamicMinIoU = (lockedMinIouBase - missCount * 0.0015f)
            .coerceAtLeast(lockedMinIouMin)
        
        var dynamicAreaThreshold = (lockedAreaChangeThreshold + missCount * 0.03f)
            .coerceAtMost(lockedAreaChangeMax)

        // 작은 객체에 대해 면적 체크 완화
        val isSmallTarget = (predictedBox.w * predictedBox.h) < 0.04f 
        if (isSmallTarget) {
            dynamicAreaThreshold *= 1.5f 
        }

        // 오랫동안 놓치고 있다면, 신뢰도가 높고 매우 가까운 경우 IoU가 0이어도 매칭을 허용합니다.
        val desperateMode = missCount > 5
        val singleCandidateMode = detections.size == 1 && missCount > 0
        val multiCandidateMode = detections.size > 1
        val centerThreshold = if (singleCandidateMode) {
            (dynamicCenterThreshold * 1.2f).coerceAtMost(1.0f)
        } else {
            dynamicCenterThreshold
        }
        val minIoUThreshold = if (singleCandidateMode) 0f else dynamicMinIoU
        val effectiveMinIoUThreshold = if (multiCandidateMode) {
            max(minIoUThreshold, lockedMultiCandidateMinIoU)
        } else {
            minIoUThreshold
        }

        for (detection in detections) {
            val iou = calculateIoU(predictedBox, detection)
            val centerDistance = calculateCenterDistance(predictedBox, detection)
            val areaChange = calculateRelativeAreaChange(predictedBox, detection)
            
            // 후보 분석을 위한 디버그 로그
            val logPrefix = "[IoUTracker] Candidate(Cnf=${"%.2f".format(detection.cnf)})"

            // 1. 중심 거리 체크
            if (centerDistance > centerThreshold) {
                if (missCount > 20) Log.d("[IoUTracker]","$logPrefix REJECT: Dist($centerDistance) > Thresh($centerThreshold)")
                continue
            }
            

            // 2. 면적 변화 체크 (축소 허용)
            // 급격한 줌인/아웃이나 움직임으로 인해 면적이 크게 변할 수 있습니다.
            // 신뢰도가 매우 높다면(>0.8), 크기가 달라도 같은 물체일 확률이 높으므로 제한을 완화합니다.
            val areaRatio = (detection.w * detection.h) / (predictedBox.w * predictedBox.h)
            val shrinkBoost = if (areaRatio < 1f) 1.4f else 1.0f // 축소되는 경우는 좀 더 관대하게 허용
            val effectiveAreaThreshold = dynamicAreaThreshold * shrinkBoost * if (singleCandidateMode) 1.6f else 1.0f
            
            // [수정] 고신뢰도 탐지의 경우 면적 체크를 우회(Bypass)합니다.
            val isHighConfidence = detection.cnf > 0.80f
            
            if (areaChange > effectiveAreaThreshold) {
                if (isHighConfidence) {
                    if (missCount > 20) Log.d("[IoUTracker]", "$logPrefix BYPASS: AreaChange($areaChange) > Thresh($effectiveAreaThreshold) but High Confidence")
                } else {
                    if (missCount > 20) Log.d("[IoUTracker]", "$logPrefix REJECT: AreaChange($areaChange) > Thresh($effectiveAreaThreshold)")
                    continue 
                }
            }
            
            // 3. 크기 비율 체크 (배경 거부)
            val areaRatioCheck = areaRatio
            
            // 로그상 유효한 타겟이 0.44에서 거부되어서 0.5 -> 0.3으로 완화함
            if (areaRatioCheck < 0.3f) {
                if (missCount > 20) Log.d("[IoUTracker]", "$logPrefix REJECT: Too Small (Ratio=$areaRatioCheck < 0.3)")
                continue 
            }

            val centerScore = 1f - (centerDistance / centerThreshold).coerceAtMost(1f)
            val areaScore = 1f - (areaChange / effectiveAreaThreshold).coerceAtMost(1f)
            
            var score = iou * 0.5f + centerScore * 0.4f + areaScore * 0.1f

            // 작은 물체 -> 크고 선명한 물체로 전환 시 점수 부스트
            if (isSmallTarget) {
                val isLargeCandidate = (detection.w * detection.h) > (predictedBox.w * predictedBox.h) * 2.0f
                val isHighConf = detection.cnf > 0.8f
                val isCloseEnough = centerScore > 0.7f

                if (isLargeCandidate && isHighConf && isCloseEnough && singleCandidateMode) {
                    score += 0.5f
                    Log.d("[IoUTracker]", "BOOST: Switching from Small to Large/Clear Target!")
                }
            }
            
            // 4. 최소 IoU 체크 및 절박 매칭(Desperate Match)
            val minIoUCheck = iou >= effectiveMinIoUThreshold
            
            if (!minIoUCheck) {
                val allowDesperateMulti =
                    multiCandidateMode &&
                        iou >= lockedMultiCandidateMinIoU &&
                        detection.cnf > 0.85f &&
                        centerScore > 0.75f &&
                        areaRatioCheck > 0.5f

                if ((singleCandidateMode || allowDesperateMulti) &&
                    detection.cnf > lockedDesperateConfidence &&
                    centerScore > lockedDesperateCenterScore &&
                    areaRatioCheck > 0.25f
                ) {
                    // IoU가 0이지만, 신뢰도가 높고 매우 가까움.
                    // 허용함!
                    if (missCount > 20) Log.d("[IoUTracker]", "$logPrefix DESPERATE MATCH! Score=$score IoU=$iou")
                } else {
                    if (missCount > 20) Log.d("[IoUTracker]", "$logPrefix SCORE($score) BUT IoU($iou) < Min($dynamicMinIoU)")
                    continue
                }
            } else {
                 if (missCount > 20) Log.d("[IoUTracker]", "$logPrefix MATCH? Score=$score IoU=$iou")
            }

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
        return sqrt(dx * dx + dy * dy)
    }

    private fun calculateRelativeAreaChange(boxA: BoundingBox, boxB: BoundingBox): Float {
        val areaA = boxA.w * boxA.h
        val areaB = boxB.w * boxB.h
        if (areaA == 0f) return 1f
        return kotlin.math.abs(areaA - areaB) / areaA
    }

    private fun predictNextPosition(track: TrackedObject, missFrames: Int): BoundingBox {
        // 속도 감쇠 및 거리 제한을 적용한 예측 기능
        
        // 과도한 움직임을 방지하기 위해 속도 캡(limit) 적용
        val cappedVx = track.vx.coerceIn(-maxVelocity, maxVelocity)
        val cappedVy = track.vy.coerceIn(-maxVelocity, maxVelocity)
        
        // 감쇠 모델 적용: 물체가 놓쳐질수록 움직임 추정치를 줄임
        val decayRate = 0.85f // 시간이 지날수록 예측 신뢰도 소폭 감소
        val decayFactor = decayRate.pow(missFrames)
        
        // 예측이 합리적인 거리 내에 머물도록 제한 (과도한 드리프트 방지) - 축소됨
        val maxPredictionDistance = 0.06f //  0.12f에서 0.06f로 축소 (화면의 최대 6% 움직임)
        
        val rawDx = cappedVx * missFrames * decayFactor
        val rawDy = cappedVy * missFrames * decayFactor
        
        // 드리프트 방지를 위해 총 변위 제한
        val displacementMagnitude = sqrt(rawDx * rawDx + rawDy * rawDy)
        val totalDx = if (displacementMagnitude > maxPredictionDistance) {
            rawDx * (maxPredictionDistance / displacementMagnitude)
        } else rawDx
        
        val totalDy = if (displacementMagnitude > maxPredictionDistance) {
            rawDy * (maxPredictionDistance / displacementMagnitude)
        } else rawDy
        
        Log.d("[IoUTracker]", "Prediction: Miss=$missFrames TotalDX=$totalDx TotalDY=$totalDy (Decay=${"%.3f".format(decayFactor)})")

        val box = track.boundingBox
        val predictedCx = box.cx + totalDx
        val predictedCy = box.cy + totalDy

        val dx = predictedCx - box.cx
        val dy = predictedCy - box.cy

        return BoundingBox(
            x1 = box.x1 + dx,
            y1 = box.y1 + dy,
            x2 = box.x2 + dx,
            y2 = box.y2 + dy,
            cx = predictedCx,
            cy = predictedCy,
            w = box.w,
            h = box.h,
            cnf = box.cnf,
            cls = box.cls,
            clsName = box.clsName
        )
    }

    private fun smoothBox(oldBox: BoundingBox, newBox: BoundingBox, alpha: Float): BoundingBox {
         // 스무딩 적용
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

    // 속도를 포함하여 업데이트된 TrackedObject를 생성하는 헬퍼 함수
    private fun createUpdatedTrack(
        id: Int, 
        smoothingBaseBox: BoundingBox, 
        velocityRefBox: BoundingBox, 
        prevVx: Float,
        prevVy: Float,
        newBox: BoundingBox, 
        alpha: Float
    ): TrackedObject {
        // 'smoothingBaseBox' (예측값 또는 이전값)을 기준으로 스무딩
        val smoothedBox = smoothBox(smoothingBaseBox, newBox, alpha)
        
        // '실제' 이전 프레임으로부터의 이동(변위)에 기반하여 속도 계산
        val instantaneousVx = smoothedBox.cx - velocityRefBox.cx
        val instantaneousVy = smoothedBox.cy - velocityRefBox.cy
        
        // [안정화 수정]: 속도에 EMA (지수 이동 평균) 적용
        // 새로운 속도 = 이전 값 30% + 새 값 70% (반응성 향상)
        // 박스가 앞뒤로 떨리는 노이즈를 줄이면서 실제 움직임 변화에는 더 빠르게 반응
        val smoothedVx = prevVx * 0.3f + instantaneousVx * 0.7f
        val smoothedVy = prevVy * 0.3f + instantaneousVy * 0.7f
        
        // 스무딩 이후 속도 캡(limit) 적용
        val finalVx = smoothedVx.coerceIn(-maxVelocity, maxVelocity)
        val finalVy = smoothedVy.coerceIn(-maxVelocity, maxVelocity)
        
        Log.d("[IoUTracker]", "[IoUTracker] Updated Track. InstV=($instantaneousVx, $instantaneousVy) SmoothedV=($finalVx, $finalVy)")
        
        return TrackedObject(id, smoothedBox, System.currentTimeMillis(), finalVx, finalVy)
    }

    /**
     * 겹침/가림(occlusion) 상황으로 인해 타겟을 전환해야 하는지 감지
     * 전환 조건:
     * 1. 현재 타겟이 상당 기간 동안 놓쳐진 상태 (가려졌을 가능성)
     * 2. 실제로 겹쳐있음을 시사하는 높은 신뢰도의 탐지가 여러 개 존재
     * 3. 탐지된 객체들이 공간적으로 뭉쳐있어(clustered) 말들이 그룹을 이룬 것으로 보임
     * 4. 그중 하나의 탐지가 다른 것들보다 훨씬 더 좋고 예측 위치에 가까움
     */
    private fun detectOverlapSwitchNeeded(
        currentPrediction: BoundingBox,
        detections: List<BoundingBox>,
        missCount: Int
    ): Boolean {
        // 상당 기간 놓치지 않았다면 전환 고려 안 함
        // 더 보수적으로 임계값 증가
        if (missCount < 25) return false
        
        // 높은 신뢰도의 탐지만 필터링
        val highConfDetections = detections.filter { it.cnf > 0.75f }
        if (highConfDetections.size < 2) return false
        
        // 실제 예측 위치 근처에 있는 탐지 찾기
        val nearbyDetections = highConfDetections.filter { detection ->
            val distance = calculateCenterDistance(currentPrediction, detection)
            distance < 0.2f // 더 엄격한 근접성 요구
        }
        
        // 예측 위치 근처에 탐지가 없으면 겹침 상황 아님
        if (nearbyDetections.isEmpty()) return false
        
        // 공간적 클러스터링 확인 (말들이 뭉쳐있는지)
        var clusterCount = 0
        for (i in highConfDetections.indices) {
            for (j in (i + 1) until highConfDetections.size) {
                val distance = calculateCenterDistance(highConfDetections[i], highConfDetections[j])
                if (distance < 0.15f) { // 매우 가깝게 붙어있음
                    clusterCount++
                }
            }
        }
        
        // 겹침 시나리오를 위해서는 공간적 뭉침 증거가 필요
        if (clusterCount == 0) return false
        
        // 근처 탐지 중 가장 좋은 후보 찾기
        val bestCandidate = nearbyDetections.maxByOrNull { detection ->
            val centerScore = 1f - calculateCenterDistance(currentPrediction, detection)
            val confScore = detection.cnf
            val areaScore = 1f - calculateRelativeAreaChange(currentPrediction, detection).coerceAtMost(1f)
            
            confScore * 0.6f + centerScore * 0.3f + areaScore * 0.1f
        }
        
        // 겹침 전환을 위한 매우 엄격한 요구사항
        val shouldSwitch = bestCandidate != null && 
                          bestCandidate.cnf > 0.85f && 
                          clusterCount >= 2 && // 여러 마리가 뭉쳐있음
                          nearbyDetections.size >= 2 // 예측 위치 근처에 여러 옵션 존재
        
        if (shouldSwitch) {
            Log.d("[IoUTracker]", "OverlapSwitch: Detected overlap scenario - ${nearbyDetections.size} nearby horses, $clusterCount clusters, best cnf=${bestCandidate.cnf}")
        } else if (missCount > 30) {
            Log.d("[IoUTracker]", "OverlapSwitch: NO overlap detected - nearby=${nearbyDetections.size}, clusters=${clusterCount}, totalDetections=${detections.size}")
        }
        
        return shouldSwitch
    }
}
