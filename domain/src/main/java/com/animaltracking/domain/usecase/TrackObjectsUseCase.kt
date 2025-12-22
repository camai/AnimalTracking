package com.animaltracking.domain.usecase

import android.graphics.Bitmap
import com.animaltracking.domain.error.DomainError
import com.animaltracking.domain.model.TrackedObject
import com.animaltracking.domain.result.DomainResult

interface TrackObjectsUseCase {
    fun track(image: Bitmap, rotation: Int): DomainResult<TrackingResult, DomainError>
    fun toggleLock(trackedObject: TrackedObject)
    fun reset()
}

data class TrackingResult(
    val trackedObjects: List<TrackedObject>,
    val lockedObjectId: Int?
)
