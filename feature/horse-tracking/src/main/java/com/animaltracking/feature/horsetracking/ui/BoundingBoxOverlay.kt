package com.animaltracking.feature.horsetracking.ui

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
import com.animaltracking.core.tracking.model.TrackedObject

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

        // 종횡비 유지용 스케일 계산 (Aspect Fill / FILL_CENTER)
        val scale = kotlin.math.max(width / imageWidth, height / imageHeight)

        // 뷰 안에서 렌더링되는 이미지 실제 크기 계산
        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale

        // 이미지 중앙 정렬을 위한 오프셋 계산 (FILL_CENTER 동작)
        val offsetX = (width - scaledImageWidth) / 2f
        val offsetY = (height - scaledImageHeight) / 2f

        // 터치 처리
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(trackedObjects, offsetX, offsetY, scaledImageWidth, scaledImageHeight) {
                    detectTapGestures { tapOffset ->
                        val tapX = tapOffset.x
                        val tapY = tapOffset.y

                        // 탭 좌표를 정규화된 이미지 좌표로 변환
                        val normX = (tapX - offsetX) / scaledImageWidth
                        val normY = (tapY - offsetY) / scaledImageHeight

                        android.util.Log.d("TrackingOverlay", "Tap: ($tapX, $tapY) -> Norm: ($normX, $normY)")
                        android.util.Log.d("TrackingOverlay", "ImageArea: Offset($offsetX, $offsetY), Size($scaledImageWidth, $scaledImageHeight)")

                        // 터치된 객체 찾기(겹칠 경우 상단 우선으로 역순 탐색)
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

        // 텍스트용 Paint 설정
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 50f
            isFakeBoldText = true
        }
        
        val greenColor = Color(0xFF00FF00)
        val whiteColor = Color(0xFFFFFFFF)

        // 각 객체 렌더링
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
                    // 스케일된 크기와 오프셋을 박스에 전달
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

    // 목표 값(정규화 좌표 * 렌더 크기 + 오프셋)
    val targetLeft = (box.x1 * renderWidth) + offsetX
    val targetTop = (box.y1 * renderHeight) + offsetY
    val targetWidth = box.w * renderWidth
    val targetHeight = box.h * renderHeight

    // 부드러운 이동을 위한 좌표 애니메이션
    val animatedLeft by animateFloatAsState(targetValue = targetLeft, label = "left")
    val animatedTop by animateFloatAsState(targetValue = targetTop, label = "top")
    val animatedWidth by animateFloatAsState(targetValue = targetWidth, label = "width")
    val animatedHeight by animateFloatAsState(targetValue = targetHeight, label = "height")

    // 개별 박스 그리기
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(animatedLeft, animatedTop),
            size = Size(animatedWidth, animatedHeight),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 8f)
        )

        // 텍스트 라벨 그리기
        drawContext.canvas.nativeCanvas.drawText(
            "Horse ${trackedObject.id} ${(box.cnf * 100).toInt()}%",
            animatedLeft,
            animatedTop - 20, // 박스보다 약간 위로
            paint
        )
    }
}
