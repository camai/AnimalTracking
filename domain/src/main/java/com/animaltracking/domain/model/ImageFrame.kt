package com.animaltracking.domain.model

data class ImageFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val format: ImageFormat = ImageFormat.UNKNOWN
)

enum class ImageFormat {
    RGBA_8888,
    YUV_420_888,
    UNKNOWN
}
