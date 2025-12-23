package com.animaltracking.data.repository

import android.graphics.Bitmap
import com.animaltracking.ai.api.DetectionBox
import com.animaltracking.ai.api.Detector
import com.animaltracking.core.tracking.model.BoundingBox
import com.animaltracking.data.error.DataError
import com.animaltracking.data.result.DataResult
import com.animaltracking.domain.error.DomainError
import com.animaltracking.domain.model.ImageFormat
import com.animaltracking.domain.model.ImageFrame
import com.animaltracking.domain.repository.ObjectDetector
import com.animaltracking.domain.result.DomainResult
import javax.inject.Inject
import java.nio.ByteBuffer

class TFLiteObjectDetector @Inject constructor(
    private val detector: Detector
) : ObjectDetector {

    override fun detect(
        imageFrame: ImageFrame
    ): DomainResult<List<BoundingBox>, DomainError> {
        return when (val result = detectInternal(imageFrame)) {
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
        imageFrame: ImageFrame
    ): DataResult<List<DetectionBox>> {
        return try {
            val bitmap = imageFrameToBitmap(imageFrame)
            val detections = detector.detect(bitmap, imageFrame.rotationDegrees)
            DataResult.Success(detections)
        } catch (e: Exception) {
            DataResult.Fail(DataError.Unexpected(e))
        }
    }

    private fun imageFrameToBitmap(imageFrame: ImageFrame): Bitmap {
        return when (imageFrame.format) {
            ImageFormat.RGBA_8888 -> {
                require(imageFrame.bytes.isNotEmpty()) { "ImageFrame bytes are empty" }
                val bitmap = Bitmap.createBitmap(
                    imageFrame.width,
                    imageFrame.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(imageFrame.bytes))
                bitmap
            }

            ImageFormat.YUV_420_888,
            ImageFormat.UNKNOWN -> {
                throw IllegalArgumentException("Unsupported image format: ${imageFrame.format}")
            }
        }
    }

    private fun DataError.toDomainError(): DomainError {
        return when (this) {
            DataError.DetectorUnavailable -> DomainError.DetectorUnavailable
            is DataError.Unexpected -> DomainError.Unexpected(throwable)
        }
    }
}
