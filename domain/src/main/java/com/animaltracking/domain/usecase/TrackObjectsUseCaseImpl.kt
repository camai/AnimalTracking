package com.animaltracking.domain.usecase

import com.animaltracking.core.tracking.model.TrackedObject
import com.animaltracking.core.tracking.repository.ObjectTracker
import com.animaltracking.domain.error.DomainError
import com.animaltracking.domain.model.ImageFrame
import com.animaltracking.domain.repository.ObjectDetector
import com.animaltracking.domain.result.DomainResult
import javax.inject.Inject

class TrackObjectsUseCaseImpl @Inject constructor(
    private val objectDetector: ObjectDetector,
    private val objectTracker: ObjectTracker
) : TrackObjectsUseCase {
    override fun track(
        imageFrame: ImageFrame
    ): DomainResult<TrackingResult, DomainError> {
        return when (val detectionResult = objectDetector.detect(imageFrame)) {
            is DomainResult.Success -> {
                try {
                    val detections = detectionResult.data
                    println("[TrackObjectsUseCase] Detections: ${detections.size} objects, confidences=[${detections.joinToString { "%.2f".format(it.cnf) }}]")
                    
                    val trackedObjects = objectTracker.track(detections)
                    val lockedObjectId = objectTracker.getLockId()
                    
                    println("[TrackObjectsUseCase] Tracked: ${trackedObjects.size} objects, locked=${lockedObjectId}")
                    
                    DomainResult.Success(
                        TrackingResult(
                            trackedObjects = trackedObjects,
                            lockedObjectId = lockedObjectId
                        )
                    )
                } catch (e: Exception) {
                    DomainResult.Failure(DomainError.Unexpected(e))
                }
            }

            is DomainResult.Failure -> DomainResult.Failure(detectionResult.error)
        }
    }

    override fun toggleLock(trackedObject: TrackedObject) {
        val currentLock = objectTracker.getLockId()
        if (currentLock == trackedObject.id) {
            objectTracker.setLockId(null)
        } else {
            objectTracker.setLockId(trackedObject.id)
        }
    }

    override fun reset() {
        objectTracker.reset()
    }
}
