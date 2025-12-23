package com.animaltracking.core.tracking.model

data class TrackedObject(
    val id: Int,
    val boundingBox: BoundingBox,
    val timestamp: Long = System.currentTimeMillis(),
    val vx: Float = 0f,
    val vy: Float = 0f
)