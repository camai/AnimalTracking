package com.animaltracking.domain.repository

import com.animaltracking.core.tracking.model.BoundingBox
import com.animaltracking.domain.error.DomainError
import com.animaltracking.domain.model.ImageFrame
import com.animaltracking.domain.result.DomainResult

interface ObjectDetector {
    fun detect(imageFrame: ImageFrame): DomainResult<List<BoundingBox>, DomainError>
}
