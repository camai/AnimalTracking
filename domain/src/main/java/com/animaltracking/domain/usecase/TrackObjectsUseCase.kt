package com.animaltracking.domain.usecase

import com.animaltracking.core.tracking.model.TrackedObject
import com.animaltracking.domain.error.DomainError
import com.animaltracking.domain.model.ImageFrame
import com.animaltracking.domain.result.DomainResult

interface TrackObjectsUseCase {
    fun track(imageFrame: ImageFrame): DomainResult<TrackingResult, DomainError>
    fun toggleLock(trackedObject: TrackedObject)
    fun reset()
}

data class TrackingResult(
    val trackedObjects: List<TrackedObject>,
    val lockedObjectId: Int?
)
