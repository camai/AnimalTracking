package com.animaltracking.data.repository

import android.graphics.Bitmap
import com.animaltracking.ai.api.Detector
import com.animaltracking.domain.model.BoundingBox
import com.animaltracking.domain.repository.ObjectDetector
import javax.inject.Inject

class TFLiteObjectDetector @Inject constructor(
    private val detector: Detector
) : ObjectDetector {

    override fun detect(image: Bitmap, rotation: Int): List<BoundingBox> {
        // Delegate detection to the AI module
        val detections = detector.detect(image, rotation)

        // Map DetectionBox (AI) to BoundingBox (Domain)
        return detections.map { detection ->
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
    }
}
