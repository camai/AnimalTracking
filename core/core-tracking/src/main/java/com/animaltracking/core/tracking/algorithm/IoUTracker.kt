package com.animaltracking.core.tracking.algorithm

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
    private val iouThreshold = 0.15f
    private val lockedCenterDistanceBase = 0.3f
    private val lockedCenterDistanceMax = 0.75f
    private val lockedAreaChangeThreshold = 0.85f
    private val lockedAreaChangeMax = 1.3f
    private val lockedScoreMin = 0.18f
    private val lockedScoreMargin = 0.12f
    private val lockedMinIouBase = 0.08f
    private val lockedMinIouMin = 0.015f

    private val maxFrameMiss = 30 // Stickiness: 1 second
    private val lockedMaxFrameMiss = 90
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
        if (lockId != null) {
            return trackLocked(detections)
        }

        val newTrackedObjects = ArrayList<TrackedObject>()

        // 1. Match active tracks
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

        // Update matched
        for ((oldTrack, detection) in matches) {
            val updatedTrack = createUpdatedTrack(
                id = oldTrack.id, 
                smoothingBaseBox = oldTrack.boundingBox, // Standard: smooth from old
                velocityRefBox = oldTrack.boundingBox,   // Standard: velocity from old
                prevVx = oldTrack.vx, // Pass previous velocity for smoothing
                prevVy = oldTrack.vy,
                newBox = detection, 
                alpha = 0.7f
            )
            tracks[oldTrack.id] = 0 to updatedTrack
            newTrackedObjects.add(updatedTrack)
        }

        // Handle Disappeared
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

        // Handle New Objects (Auto-Lock Init)
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
              // Standard add
             for (detection in unmatchedDetections) {
                 val newId = nextObjectId++
                 val newTrack = TrackedObject(newId, detection)
                 tracks[newId] = 0 to newTrack
                 newTrackedObjects.add(newTrack)
             }
        }

        // Auto-Lock Logic
        if (lockId == null && newTrackedObjects.isNotEmpty()) {
            val bestCandidate = newTrackedObjects.maxByOrNull { obj ->
                val box = obj.boundingBox
                val areaScore = box.w * box.h
                val confScore = box.cnf
                val centerScore = 1.0f - kotlin.math.abs(box.cx - 0.5f) * 2
                confScore * 2.0f + centerScore * 1.5f + areaScore
            }

            if (bestCandidate != null && bestCandidate.boundingBox.cnf > 0.6f) {
                lockId = bestCandidate.id
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
    }

    private fun trackLocked(detections: List<BoundingBox>): List<TrackedObject> {
        val lockedId = lockId ?: return emptyList()
        val lockedEntry = tracks[lockedId]
        val lockedTrack = lockedEntry?.second
        val missCount = lockedEntry?.first ?: 0

        if (lockedTrack != null) {
            println("[IoUTracker] Frame Start. ID=$lockedId Miss=$missCount VX=${lockedTrack.vx} VY=${lockedTrack.vy}")
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

        // 1. Predict where the object SHOULD be in this frame (1 frame ahead of last update)
        // This solves the "lag" issue by matching against the estimated future position, 
        // effectively canceling out the delay.
        val predictedBox = predictNextPosition(lockedTrack, missCount + 1)

        // 2. Match against Prediction
        val match = selectLockedMatch(predictedBox, detections, missCount)
        val hasCandidate = match.detection != null
        val isAmbiguous = hasCandidate &&
            match.secondScore >= 0f &&
            (match.bestScore - match.secondScore) < lockedScoreMargin
        val isStrongEnough = hasCandidate && match.bestScore >= lockedScoreMin

        return if (isStrongEnough && !isAmbiguous) {
            println("[IoUTracker] MATCH FOUND! Score=${match.bestScore}")
            // 3. Update using Prediction as the baseline for smoothing
            // This prevents "snapping back" to the old position.
            val updatedTrack = createUpdatedTrack(
                id = lockedTrack.id, 
                smoothingBaseBox = predictedBox,       // Locked: smooth from PREDICTION (no lag)
                velocityRefBox = lockedTrack.boundingBox, // Locked: velocity from PREVIOUS (correct displacement)
                prevVx = lockedTrack.vx, // Smoothing
                prevVy = lockedTrack.vy,
                newBox = match.detection!!, 
                alpha = 0.7f
            )
            tracks[lockedId] = 0 to updatedTrack
            listOf(updatedTrack)
        } else {
            val nextMiss = kotlin.math.min(missCount + 1, lockedMaxFrameMiss)
            println("[IoUTracker] MISSING... Count=$nextMiss")  

            // [Before: Smart Unlock Logic was here]
            // Removed to enforce strict single-target tracking.
            // The tracker will now blindly predict the position until lockedMaxFrameMiss is reached,
            // without ever switching to a nearby candidate.

            tracks[lockedId] = nextMiss to lockedTrack
            // Just use the prediction we already calculated
            val predictedTrack = TrackedObject(lockedTrack.id, predictNextPosition(lockedTrack, nextMiss))
            listOf(predictedTrack)
        }
    }

    private fun selectLockedMatch(
        predictedBox: BoundingBox, // Changed from lockedTrack
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
        // Stricter area check to prevent switching to larger/smaller horses during intersection
        val dynamicAreaThreshold = (lockedAreaChangeThreshold + missCount * 0.03f)
            .coerceAtMost(lockedAreaChangeMax)

        for (detection in detections) {
            val iou = calculateIoU(predictedBox, detection)
            val centerDistance = calculateCenterDistance(predictedBox, detection)
            val areaChange = calculateRelativeAreaChange(predictedBox, detection)

            if (centerDistance > dynamicCenterThreshold) continue
            // Hard reject if size difference is too massive (e.g. switching from far horse to near horse)
            if (areaChange > dynamicAreaThreshold) continue 
            
            // Explicitly reject if the new candidate is significantly SMALLER (background object)
            // If new area is < 50% of old area, it's likely a background horse.
            val areaRatio = (detection.w * detection.h) / (predictedBox.w * predictedBox.h)
            if (areaRatio < 0.5f) continue 

            val centerScore = 1f - (centerDistance / dynamicCenterThreshold).coerceAtMost(1f)
            val areaScore = 1f - (areaChange / dynamicAreaThreshold).coerceAtMost(1f)
            
            // Increased weight for Center Score to prefer trajectory adherence
            val score = iou * 0.5f + centerScore * 0.4f + areaScore * 0.1f

            if (score > bestScore && iou >= dynamicMinIoU) {
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
        
        // Return signed ratio? No, we need deviation.
        // But we want to punish 'Shrinking' more if we think it's background switching.
        // For now, simple absolute difference ratio.
        return kotlin.math.abs(areaA - areaB) / areaA
    }

    private fun predictNextPosition(track: TrackedObject, missFrames: Int): BoundingBox {
        // [Fix]: Use Cumulative Displacement (Geometric Series Sum)
        // Previously: Dist = V * t * r^t (This goes to 0 as t increases -> Returns to origin)
        // Now: Dist = Coasting + Braking Sum
        
        // [Stabilization Fix]: Reduced coasting frames from 5 to 3
        // To prevent overshooting when velocity changes direction quickly (oscillation).
        val coastingFrames = kotlin.math.min(missFrames, 3) 
        val brakingFrames = kotlin.math.max(0, missFrames - 3)

        // 1. Coasting Phase (Constant Velocity)
        val coastingDx = track.vx * coastingFrames
        val coastingDy = track.vy * coastingFrames

        // 2. Braking Phase (Geometric Decay Sum)
        // Sum = V * (0.9 + 0.9^2 + ... + 0.9^n)
        // Formula: a(1-r^n)/(1-r) where a=0.9V, r=0.9
        // sumFactor = 0.9 * (1 - 0.9^n) / (1 - 0.9) = 0.9 / 0.1 * (...) = 9.0 * (1 - 0.9^n)
        var brakingDx = 0f
        var brakingDy = 0f
        
        if (brakingFrames > 0) {
            val sumFactor = 9.0f * (1.0f - 0.9f.pow(brakingFrames))
            brakingDx = track.vx * sumFactor
            brakingDy = track.vy * sumFactor
        }

        val totalDx = coastingDx + brakingDx
        val totalDy = coastingDy + brakingDy
        
        println("[IoUTracker] Prediction: Miss=$missFrames TotalDX=$totalDx TotalDY=$totalDy (Coast=$coastingFrames Brake=$brakingFrames)")

        val box = track.boundingBox
        val predictedCx = box.cx + totalDx
        val predictedCy = box.cy + totalDy

        // Limit movement to reasonable bounds (optional, but good for safety)
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
         // Calculate simple velocity (New - Old)
         val vx = newBox.cx - oldBox.cx
         val vy = newBox.cy - oldBox.cy

         // Apply smoothing
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

    // Helper to create updated TrackedObject with velocity
    private fun createUpdatedTrack(
        id: Int, 
        smoothingBaseBox: BoundingBox, 
        velocityRefBox: BoundingBox, 
        prevVx: Float,
        prevVy: Float,
        newBox: BoundingBox, 
        alpha: Float
    ): TrackedObject {
        // Smooth against the 'smoothingBaseBox' (could be Predicted or Old)
        val smoothedBox = smoothBox(smoothingBaseBox, newBox, alpha)
        
        // Calculate velocity based on ACTUAL movement (Displacement) from the REAL previous frame
        val instantaneousVx = smoothedBox.cx - velocityRefBox.cx
        val instantaneousVy = smoothedBox.cy - velocityRefBox.cy
        
        // [Stabilization Fix]: Apply EMA (Exponential Moving Average) to velocity
        // NewVelocity = 40% Old + 60% New
        // This reduces noise/oscillation when the box jitters back and forth.
        val smoothedVx = prevVx * 0.4f + instantaneousVx * 0.6f
        val smoothedVy = prevVy * 0.4f + instantaneousVy * 0.6f
        
        println("[IoUTracker] Updated Track. InstV=($instantaneousVx, $instantaneousVy) SmoothedV=($smoothedVx, $smoothedVy)")
        
        return TrackedObject(id, smoothedBox, System.currentTimeMillis(), smoothedVx, smoothedVy)
    }
}