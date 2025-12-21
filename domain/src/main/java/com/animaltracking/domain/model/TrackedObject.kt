package com.animaltracking.domain.model

data class TrackedObject(
    val id: Int,
    val boundingBox: BoundingBox,
    val timestamp: Long = System.currentTimeMillis()
    // Future: velocity, trajectory history, etc.
)
