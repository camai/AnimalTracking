package com.animaltracking.domain.repository

import com.animaltracking.domain.model.BoundingBox
import com.animaltracking.domain.model.TrackedObject

interface ObjectTracker {
    fun track(detections: List<BoundingBox>): List<TrackedObject>
    fun reset()
    fun setLockId(id: Int?)
    fun getLockId(): Int?
}
