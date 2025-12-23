package com.animaltracking.feature.horsetracking.screen

import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.animaltracking.core.tracking.model.TrackedObject
import com.animaltracking.domain.error.DomainError
import com.animaltracking.feature.camera.CameraPreviewWithPermission
import com.animaltracking.feature.horsetracking.R
import com.animaltracking.feature.horsetracking.ui.BoundingBoxOverlay
import com.animaltracking.feature.horsetracking.viewmodel.TrackingEvent
import com.animaltracking.feature.horsetracking.viewmodel.TrackingViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
@Suppress("LocalContextGetResourceValueCall")
internal fun TrackingRoute(
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TrackingEvent.ShowError -> {
                    val message = context.getString(event.error.toMessageRes())
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    TrackingScreen(
        onFrameReceived = viewModel::onFrameReceived,
        trackedObjects = uiState.trackedObjects,
        frameSize = uiState.frameSize,
        lockedObjectId = uiState.lockedObjectId,
        onObjectClicked = viewModel::toggleObjectLock
    )
}

@Composable
private fun TrackingScreen(
    onFrameReceived: (ImageProxy) -> Unit,
    trackedObjects: List<TrackedObject> = emptyList(),
    frameSize: Pair<Int, Int>? = null,
    lockedObjectId: Int? = null,
    onObjectClicked: (TrackedObject) -> Unit = {}
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        CameraPreviewWithPermission(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            onFrameReceived = onFrameReceived
        ) {
            BoundingBoxOverlay(
                trackedObjects = trackedObjects,
                imageWidth = frameSize?.first ?: 320,
                imageHeight = frameSize?.second ?: 320,
                lockedObjectId = lockedObjectId,
                onObjectClick = onObjectClicked
            )
        }
    }
}

private fun DomainError.toMessageRes(): Int {
    return when (this) {
        DomainError.DetectorUnavailable -> R.string.horse_tracking_error_detector_unavailable
        is DomainError.Unexpected -> R.string.horse_tracking_error_unexpected
    }
}
