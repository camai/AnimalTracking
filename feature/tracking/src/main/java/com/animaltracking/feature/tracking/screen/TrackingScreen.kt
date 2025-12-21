package com.animaltracking.feature.tracking.screen

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animaltracking.feature.tracking.viewmodel.TrackingViewModel
import java.util.concurrent.Executors


import com.animaltracking.feature.tracking.ui.BoundingBoxOverlay

@Composable
internal fun TrackingRoute(
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TrackingScreen(
        hasPermission = uiState.hasCameraPermission,
        onPermissionResult = viewModel::onPermissionResult,
        onFrameReceived = viewModel::onFrameReceived,
        trackedObjects = uiState.trackedObjects
    )
}

@Composable
private fun TrackingScreen(
    hasPermission: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    onFrameReceived: (androidx.camera.core.ImageProxy) -> Unit,
    trackedObjects: List<com.animaltracking.domain.model.TrackedObject> = emptyList()
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            onPermissionResult(granted)
        }
    )

    // Check initial permission
    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        onPermissionResult(isGranted)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(onFrameReceived = onFrameReceived)
                com.animaltracking.feature.tracking.ui.BoundingBoxOverlay(trackedObjects = trackedObjects)
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text(text = "Request Camera Permission")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onFrameReceived: (ImageProxy) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_START
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    onFrameReceived(imageProxy)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        /* lifecycleOwner = */ lifecycleOwner,
                        /* cameraSelector = */ cameraSelector,
                        /* ...useCases = */ preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("TrackingScreen", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}
