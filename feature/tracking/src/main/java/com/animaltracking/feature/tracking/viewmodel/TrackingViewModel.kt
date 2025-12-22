package com.animaltracking.feature.tracking.viewmodel

import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animaltracking.core.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

import com.animaltracking.domain.repository.ObjectDetector
import com.animaltracking.domain.repository.ObjectTracker
import com.animaltracking.domain.model.TrackedObject
import java.util.concurrent.atomic.AtomicBoolean

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val objectDetector: ObjectDetector,
    private val objectTracker: ObjectTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
    }

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

                // Pass cachedBitmap to recycle memory
                val bitmap = BitmapUtils.imageProxyToBitmap(imageProxy, cachedBitmap)

                cachedBitmap = bitmap // Update cache with the returned bitmap (could be new or reused)

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
    val hasCameraPermission: Boolean = false,
    val trackedObjects: List<TrackedObject> = emptyList(),
    val frameSize: Pair<Int, Int>? = null, // width, height for coordinate mapping
    val lockedObjectId: Int? = null
)
