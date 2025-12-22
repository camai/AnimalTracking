package com.animaltracking.domain.repository

import android.graphics.Bitmap
import com.animaltracking.domain.error.DomainError
import com.animaltracking.domain.model.BoundingBox
import com.animaltracking.domain.result.DomainResult

interface ObjectDetector {
    fun detect(image: Bitmap, rotation: Int): DomainResult<List<BoundingBox>, DomainError>
}
