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
    private val iouThreshold = 0.5f
    private val maxFrameMiss = 5 // Allow object to be missed for a few frames

    // Simple tracking state: ID -> (MissCount, LastSeenTrackedObject)
    private val tracks = HashMap<Int, Pair<Int, TrackedObject>>()

    override fun track(detections: List<BoundingBox>): List<TrackedObject> {
        val newTrackedObjects = ArrayList<TrackedObject>()
        
        // 1. Try to match currently tracked objects with new detections
        // Simple greedy matching: 
        // Iterate through existing tracks, find best matching detection (highest IoU)
        
        val activeTracks = tracks.map { it.value.second }.toMutableList()
        val unmatchedDetections = detections.toMutableList()
        
        // Match existing tracks to new detections
        val matchedIds = HashSet<Int>()
        
        // This is a simplified O(N*M) matching. Hungarian algorithm is better for complex cases.
        // But for single object (horse) or few objects, this is sufficient.
        
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
            val updatedTrack = TrackedObject(oldTrack.id, detection)
            tracks[oldTrack.id] = 0 to updatedTrack // Reset miss count
            newTrackedObjects.add(updatedTrack)
        }
        
        // Handle Unmatched Tracks (Disappeared?)
        val disappearedIds = tracks.keys - matchedIds
        for (id in disappearedIds) {
            val (missCount, lastTrack) = tracks[id]!!
            if (missCount < maxFrameMiss) {
                tracks[id] = (missCount + 1) to lastTrack
                // Use last known position for prediction?
            } else {
                tracks.remove(id)
            }
        }
        
        // Handle Unmatched Detections (New Objects)
        // Optimization: Only start tracking new objects if we are not tracking anything?
        // OR: If we want to track MULTIPLE objects, just add them all.
        // TRD 3.2.1: "Init: Select highest confidence horse in initial frame."
        // This implies we focus on ONE target or prioritize initialization.
        
        // Strategy:
        // 1. If we have active tracks, new detections are just new candidates (or ignored if single-target mode).
        // 2. If we have NO active tracks, select the BEST detection to start tracking.
        
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
            // Logic for regular updates or multi-object addition
            // For now, let's stick to the TRD implication of "Target Selection". 
            // If we are already tracking, we don't randomly add new objects unless we explicitly want multi-tracking.
            // Let's implement multi-tracking for robustness, but initialized by confidence.
            
            for (detection in unmatchedDetections) {
                val newId = nextObjectId++
                val newTrack = TrackedObject(newId, detection)
                tracks[newId] = 0 to newTrack
                newTrackedObjects.add(newTrack)
            }
        }
        
        return newTrackedObjects
    }

    override fun reset() {
        tracks.clear()
        nextObjectId = 0
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
}
