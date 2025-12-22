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
             return yuvToRgbBitmap(image)
        }
        
        if (image.format == ImageFormat.FLEX_RGBA_8888 || image.format == PixelFormat.RGBA_8888) {
             val plane = image.planes[0]
             val buffer = plane.buffer
             val pixelStride = plane.pixelStride
             val rowStride = plane.rowStride
             val rowPadding = rowStride - pixelStride * image.width
             
             // 유효한 너비/높이 계산
             val width = image.width + rowPadding / pixelStride
             val height = image.height
             
             // reusableBitmap이 유효한지 확인
             val bitmap = if (reusableBitmap != null && 
                              reusableBitmap.width == width && 
                              reusableBitmap.height == height &&
                              !reusableBitmap.isRecycled) {
                 reusableBitmap
             } else {
                 Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
             }
             
             buffer.rewind() // 버퍼 위치를 처음으로 이동
             bitmap.copyPixelsFromBuffer(buffer)
             
             // 패딩이 있으면 크롭이 필요할 수 있지만, 보통 ML 입력에서는 리사이즈로 처리함.
             // 지금은 패딩 포함 비트맵 반환이 더 빠르지만, 정확한 크기가 필요하면:
             if (rowPadding == 0) {
                 return bitmap
             }
             
             // 참고: 크롭 비트맵 재사용은 더 어렵다.
             // 이상적으로는 패딩 비트맵을 ML에 넘겨 크롭/리사이즈하게 한다.
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
