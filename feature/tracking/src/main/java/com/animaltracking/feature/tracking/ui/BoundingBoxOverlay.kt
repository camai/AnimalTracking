package com.animaltracking.feature.tracking.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import com.animaltracking.domain.model.TrackedObject

@Composable
fun BoundingBoxOverlay(
    trackedObjects: List<TrackedObject>,
    imageWidth: Int = 320, // Model input width or ImageProxy width
    imageHeight: Int = 320 // Model input height or ImageProxy height
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Calculate scale factors to map detection coordinates to screen coordinates
        // Assuming CameraPreview fills the screen with FILL_START or similar
        // Note: This needs careful handling of aspect ratios.
        // For simplicity, assuming image fills width and keeps aspect ratio or fills screen.
        // Let's assume standard full screen preview for now.
        
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight
        
        // Use the smaller scale to fit/crop logic if needed, or stretch if exact match?
        // Let's assume the preview matches the analyze image aspect ratio effectively after crop
        // OR simply scale to screen (naive approach for MVP)
        
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 40f
        }

        trackedObjects.forEach { trackedObject ->
            val box = trackedObject.boundingBox
            
            // Map coordinates
            val left = box.x1 * scaleX
            val top = box.y1 * scaleY
            val width = box.w * scaleX
            val height = box.h * scaleY
            
            drawRect(
                color = Color.Red,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 5f)
            )
            
            // Draw ID and Confidence
            drawContext.canvas.nativeCanvas.drawText(
                "ID: ${trackedObject.id} (${String.format("%.2f", box.cnf)})",
                left,
                top - 10,
                paint
            )
        }
    }
}
