package com.animaltracking.core.android.util

import android.graphics.Bitmap
import com.animaltracking.domain.model.ImageFormat
import com.animaltracking.domain.model.ImageFrame
import java.nio.ByteBuffer

object ImageFrameMapper {
    fun fromBitmap(bitmap: Bitmap, rotationDegrees: Int): ImageFrame {
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        return ImageFrame(
            bytes = buffer.array(),
            width = bitmap.width,
            height = bitmap.height,
            rotationDegrees = rotationDegrees,
            format = ImageFormat.RGBA_8888
        )
    }
}
