package com.animaltracking.feature.horsetracking.viewmodel

import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animaltracking.core.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.animaltracking.domain.model.TrackedObject
import com.animaltracking.domain.repository.ObjectDetector
import com.animaltracking.domain.repository.ObjectTracker
import java.util.concurrent.atomic.AtomicBoolean

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val objectDetector: ObjectDetector,
    private val objectTracker: ObjectTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    fun toggleObjectLock(trackedObject: TrackedObject) {
         val currentLock = objectTracker.getLockId()
         if (currentLock == trackedObject.id) {
             objectTracker.setLockId(null)
         } else {
             objectTracker.setLockId(trackedObject.id)
         }
    }

    private val isProcessing = AtomicBoolean(false)
    private var cachedBitmap: android.graphics.Bitmap? = null
    private var frameCount = 0
    private val processEveryNthFrame = 1

    fun onFrameReceived(imageProxy: ImageProxy) {
        frameCount++
        
        if (frameCount % processEveryNthFrame != 0 || isProcessing.get()) {
            imageProxy.close()
            return
        }

        isProcessing.set(true)

        viewModelScope.launch(Dispatchers.Default) {
            try {

                // 캐시된 비트맵을 활용해 메모리 재사용
                val bitmap = BitmapUtils.imageProxyToBitmap(imageProxy, cachedBitmap)

                cachedBitmap = bitmap // 반환된 비트맵으로 캐시 갱신(신규 또는 재사용)

                if (bitmap != null) {
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val detections = objectDetector.detect(bitmap, rotation)

                    val trackedObjects = objectTracker.track(detections)
                    val lockedId = objectTracker.getLockId()
                    
                    val frameWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                    val frameHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height


                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            trackedObjects = trackedObjects,
                            frameSize = Pair(frameWidth, frameHeight),
                            lockedObjectId = lockedId
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("Tracking", "Error processing frame", e)
            } finally {
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }
}



data class TrackingUiState(
    val trackedObjects: List<TrackedObject> = emptyList(),
    val frameSize: Pair<Int, Int>? = null, // 좌표 매핑용 너비/높이
    val lockedObjectId: Int? = null
)
