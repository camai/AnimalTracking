package com.animaltracking.domain.repository

import android.graphics.Bitmap
import com.animaltracking.domain.model.BoundingBox

interface ObjectDetector {
    fun detect(image: Bitmap, rotation: Int): List<BoundingBox>
}
