package com.animaltracking.feature.horsetracking.viewmodel

import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animaltracking.core.android.util.BitmapUtils
import com.animaltracking.core.android.util.ImageFrameMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.animaltracking.core.tracking.model.TrackedObject
import com.animaltracking.domain.error.DomainError
import com.animaltracking.domain.result.DomainResult
import com.animaltracking.domain.usecase.TrackObjectsUseCase
import java.util.concurrent.atomic.AtomicBoolean

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val trackObjectsUseCase: TrackObjectsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TrackingEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TrackingEvent> = _events.asSharedFlow()

    fun toggleObjectLock(trackedObject: TrackedObject) {
        trackObjectsUseCase.toggleLock(trackedObject)
    }

    private val isProcessing = AtomicBoolean(false)
    private var cachedBitmap: android.graphics.Bitmap? = null
    private var frameCount = 0
    // 매 3프레임마다 처리하여 성능 최적화 (30fps -> 10fps 처리로 CPU/GPU 부하 감소)
    private val processEveryNthFrame = 3

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
                    val frameWidth = if (rotation == 90 || rotation == 270) {
                        imageProxy.height
                    } else {
                        imageProxy.width
                    }
                    val frameHeight = if (rotation == 90 || rotation == 270) {
                        imageProxy.width
                    } else {
                        imageProxy.height
                    }
                    val imageFrame = ImageFrameMapper.fromBitmap(bitmap, rotation)
                    when (val trackingResult = trackObjectsUseCase.track(imageFrame)) {
                        is DomainResult.Success -> {
                            val trackedObjects = trackingResult.data.trackedObjects
                            val lockedId = trackingResult.data.lockedObjectId

                            viewModelScope.launch(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(
                                    trackedObjects = trackedObjects,
                                    frameSize = Pair(frameWidth, frameHeight),
                                    lockedObjectId = lockedId
                                )
                            }
                        }

                        is DomainResult.Failure -> {
                            Log.e("Tracking", "Tracking failed: ${trackingResult.error}")
                            _events.tryEmit(TrackingEvent.ShowError(trackingResult.error))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Tracking", "Error processing frame", e)
                _events.tryEmit(TrackingEvent.ShowError(DomainError.Unexpected(e)))
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
