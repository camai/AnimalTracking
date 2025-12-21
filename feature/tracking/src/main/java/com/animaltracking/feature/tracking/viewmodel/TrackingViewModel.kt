package com.animaltracking.feature.tracking.viewmodel

import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import com.animaltracking.core.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

import com.animaltracking.domain.repository.ObjectDetector

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val objectDetector: ObjectDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
    }

    fun onFrameReceived(imageProxy: ImageProxy) {
        val bitmap = BitmapUtils.imageProxyToBitmap(imageProxy)
        if (bitmap != null) {
            val results = objectDetector.detect(bitmap, imageProxy.imageInfo.rotationDegrees)
            // Log results for now to verify
            Log.d("TrackingViewModel", "Detected: ${results.size} objects")
        }
        imageProxy.close()
    }
}

data class TrackingUiState(
    val hasCameraPermission: Boolean = false
)
