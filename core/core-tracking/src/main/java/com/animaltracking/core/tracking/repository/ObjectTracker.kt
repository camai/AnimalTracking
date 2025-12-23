package com.animaltracking.core.tracking.repository

import com.animaltracking.core.tracking.model.BoundingBox
import com.animaltracking.core.tracking.model.TrackedObject

interface ObjectTracker {
    fun track(detections: List<BoundingBox>): List<TrackedObject>
    fun reset()
    fun setLockId(id: Int?)
    fun getLockId(): Int?
}