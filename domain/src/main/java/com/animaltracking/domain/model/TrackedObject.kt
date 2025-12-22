package com.animaltracking.domain.model

data class TrackedObject(
    val id: Int,
    val boundingBox: BoundingBox,
    val timestamp: Long = System.currentTimeMillis()
    // 추후: 속도, 궤적 히스토리 등
)
