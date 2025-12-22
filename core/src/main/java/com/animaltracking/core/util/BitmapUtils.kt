package com.animaltracking.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap

object BitmapUtils {

    fun imageProxyToBitmap(image: ImageProxy, reusableBitmap: Bitmap? = null): Bitmap? {
        if (image.format == ImageFormat.YUV_420_888) {
             // YUV_420_888 변환 지원 추가
             return yuvToRgbBitmap(image)
        }
        
        if (image.format == ImageFormat.FLEX_RGBA_8888 || image.format == PixelFormat.RGBA_8888) {
             val plane = image.planes[0]
             val buffer = plane.buffer
             val pixelStride = plane.pixelStride
             val rowStride = plane.rowStride
             val rowPadding = rowStride - pixelStride * image.width
             
             // Calculate valid width/height
             val width = image.width + rowPadding / pixelStride
             val height = image.height
             
             // Check if reusableBitmap is valid
             val bitmap = if (reusableBitmap != null && 
                              reusableBitmap.width == width && 
                              reusableBitmap.height == height &&
                              !reusableBitmap.isRecycled) {
                 reusableBitmap
             } else {
                 Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
             }
             
             buffer.rewind() // Ensure buffer is at the beginning
             bitmap.copyPixelsFromBuffer(buffer)
             
             // If padding exists, we might need to crop, but usually for ML input we just resize anyway.
             // For now, returning the padded bitmap is faster, but if exact size is needed:
             if (rowPadding == 0) {
                 return bitmap
             }
             
             // Note: Reusing cropping bitmaps is harder. 
             // Ideally we pass the padded bitmap to ML and let it crop/resize.
             return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
        
        return null
    }
    
    private fun yuvToRgbBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            
            val ySize = yPlane.buffer.remaining()
            val uSize = uPlane.buffer.remaining()
            val vSize = vPlane.buffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            yPlane.buffer.get(nv21, 0, ySize)
            vPlane.buffer.get(nv21, ySize, vSize)
            uPlane.buffer.get(nv21, ySize + vSize, uSize)
            
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()
            
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
