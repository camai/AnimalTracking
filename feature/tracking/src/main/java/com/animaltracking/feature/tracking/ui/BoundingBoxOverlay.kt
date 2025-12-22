package com.animaltracking.feature.tracking.ui

import android.annotation.SuppressLint
import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.animaltracking.domain.model.TrackedObject

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun BoundingBoxOverlay(
    trackedObjects: List<TrackedObject>,
    imageWidth: Int = 320,
    imageHeight: Int = 320,
    lockedObjectId: Int? = null,
    onObjectClick: (TrackedObject) -> Unit = {}
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // Calculate scale to maintain aspect ratio (Aspect Fill / FILL_CENTER)
        val scale = kotlin.math.max(width / imageWidth, height / imageHeight)

        // Calculate the actual size of the rendered image within the view
        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale

        // Calculate offsets to center the image (FILL_CENTER behavior)
        val offsetX = (width - scaledImageWidth) / 2f
        val offsetY = (height - scaledImageHeight) / 2f

        // Touch handling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(trackedObjects, offsetX, offsetY, scaledImageWidth, scaledImageHeight) {
                    detectTapGestures { tapOffset ->
                        val tapX = tapOffset.x
                        val tapY = tapOffset.y

                        // Convert tap to normalized image coordinates
                        val normX = (tapX - offsetX) / scaledImageWidth
                        val normY = (tapY - offsetY) / scaledImageHeight

                        android.util.Log.d("TrackingOverlay", "Tap: ($tapX, $tapY) -> Norm: ($normX, $normY)")
                        android.util.Log.d("TrackingOverlay", "ImageArea: Offset($offsetX, $offsetY), Size($scaledImageWidth, $scaledImageHeight)")

                        // Find touched object (reverse order to pick top-most if overlapping)
                        val touchedObject = trackedObjects.lastOrNull { obj ->
                            val box = obj.boundingBox
                            val hit = normX >= box.x1 && normX <= box.x2 && normY >= box.y1 && normY <= box.y2
                            if (hit) android.util.Log.d("TrackingOverlay", "Hit Object: ${obj.id}")
                            hit
                        }

                        if (touchedObject != null) {
                            onObjectClick(touchedObject)
                        } else {
                             android.util.Log.d("TrackingOverlay", "No object hit")
                        }
                    }
                }
        )

        // Setup Paint for text
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 50f
            isFakeBoldText = true
        }
        
        val greenColor = Color(0xFF00FF00)
        val whiteColor = Color(0xFFFFFFFF)

        // Render each object
        trackedObjects.forEach { trackedObject ->
            val isLocked = trackedObject.id == lockedObjectId
            // 녹색: 락 걸림, 흰색: 일반
            val boxColor = if (isLocked) greenColor else whiteColor
            
            // 텍스트 색상도 변경
            val textPaint = Paint(paint).apply {
                color = if (isLocked) android.graphics.Color.GREEN else android.graphics.Color.WHITE
            }

            key(trackedObject.id) {
                TrackedObjectBox(
                    trackedObject = trackedObject,
                    // Pass the scaled dimensions and offsets to the box
                    renderWidth = scaledImageWidth,
                    renderHeight = scaledImageHeight,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    primaryColor = boxColor,
                    paint = textPaint
                )
            }
        }
    }
}

@Composable
private fun TrackedObjectBox(
    trackedObject: TrackedObject,
    renderWidth: Float,
    renderHeight: Float,
    offsetX: Float,
    offsetY: Float,
    primaryColor: Color,
    paint: Paint
) {
    val box = trackedObject.boundingBox

    // Target Values (Normalized coords * Rendered Size + Offset)
    val targetLeft = (box.x1 * renderWidth) + offsetX
    val targetTop = (box.y1 * renderHeight) + offsetY
    val targetWidth = box.w * renderWidth
    val targetHeight = box.h * renderHeight

    // Animate coordinates for smooth movement
    val animatedLeft by animateFloatAsState(targetValue = targetLeft, label = "left")
    val animatedTop by animateFloatAsState(targetValue = targetTop, label = "top")
    val animatedWidth by animateFloatAsState(targetValue = targetWidth, label = "width")
    val animatedHeight by animateFloatAsState(targetValue = targetHeight, label = "height")

    // Draw the individual box
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(animatedLeft, animatedTop),
            size = Size(animatedWidth, animatedHeight),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 8f)
        )

        // Draw Text Label
        drawContext.canvas.nativeCanvas.drawText(
            "Horse ${trackedObject.id} ${(box.cnf * 100).toInt()}%",
            animatedLeft,
            animatedTop - 20, // Slightly higher than box
            paint
        )
    }
}
