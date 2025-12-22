package com.animaltracking.data.repository

import android.graphics.Bitmap
import com.animaltracking.ai.api.DetectionBox
import com.animaltracking.ai.api.Detector
import com.animaltracking.data.error.DataError
import com.animaltracking.data.result.DataResult
import com.animaltracking.domain.error.DomainError
import com.animaltracking.domain.model.BoundingBox
import com.animaltracking.domain.repository.ObjectDetector
import com.animaltracking.domain.result.DomainResult
import javax.inject.Inject

class TFLiteObjectDetector @Inject constructor(
    private val detector: Detector
) : ObjectDetector {

    override fun detect(
        image: Bitmap,
        rotation: Int
    ): DomainResult<List<BoundingBox>, DomainError> {
        return when (val result = detectInternal(image, rotation)) {
            is DataResult.Success -> {
                val mapped = result.data.map { detection ->
                    BoundingBox(
                        x1 = detection.x1,
                        y1 = detection.y1,
                        x2 = detection.x2,
                        y2 = detection.y2,
                        cx = detection.cx,
                        cy = detection.cy,
                        w = detection.w,
                        h = detection.h,
                        cnf = detection.confidence,
                        cls = detection.classId,
                        clsName = detection.className
                    )
                }
                DomainResult.Success(mapped)
            }

            is DataResult.Fail -> DomainResult.Failure(result.error.toDomainError())
        }
    }

    private fun detectInternal(
        image: Bitmap,
        rotation: Int
    ): DataResult<List<DetectionBox>> {
        return try {
            val detections = detector.detect(image, rotation)
            DataResult.Success(detections)
        } catch (e: Exception) {
            DataResult.Fail(DataError.Unexpected(e))
        }
    }

    private fun DataError.toDomainError(): DomainError {
        return when (this) {
            DataError.DetectorUnavailable -> DomainError.DetectorUnavailable
            is DataError.Unexpected -> DomainError.Unexpected(throwable)
        }
    }
}
