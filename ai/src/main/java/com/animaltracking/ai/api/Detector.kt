package com.animaltracking.ai.api

import android.graphics.Bitmap

interface Detector {
    /**
     * Detect objects in the given bitmap.
     * @param image Input image (should be upright if rotation is applied during conversion)
     * @param rotation Rotation degrees (optional usage depending on implementation)
     * @return List of detected bounding boxes (Normalized [0, 1])
     */
    fun detect(image: Bitmap, rotation: Int): List<DetectionBox>
}
