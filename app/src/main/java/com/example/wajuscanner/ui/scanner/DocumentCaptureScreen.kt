package com.example.wajuscanner.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wajuscanner.R
import com.example.wajuscanner.core.camera.CameraStabilization

/**
 * Kamera dokumen sendiri (CameraX) dengan penstabilan perkakasan — untuk pengguna
 * yang tangannya bergegar. Memintas ML Kit: tiada auto-perspektif (imej lurus kekal
 * lurus) dan kualiti penuh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCaptureScreen(
    viewModel: DocumentCaptureViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onDocumentCreated: (Long) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        val current = state
        if (current is DocumentCaptureViewModel.CaptureState.Saved) {
            onDocumentCreated(current.documentId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !hasPermission -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.capture_permission), style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text(stringResource(R.string.capture_grant)) }
                }

                state is DocumentCaptureViewModel.CaptureState.Saving -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.capture_saving), modifier = Modifier.padding(top = 12.dp))
                }

                else -> CameraCapture(viewModel)
            }

            (state as? DocumentCaptureViewModel.CaptureState.Error)?.let { error ->
                Text(
                    text = error.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraCapture(viewModel: DocumentCaptureViewModel) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val imageCaptureHolder = remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val providerState = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        runCatching { providerState.value = ProcessCameraProvider.getInstance(context).get() }
    }
    LaunchedEffect(providerState.value) {
        val provider = providerState.value ?: return@LaunchedEffect
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(95)
        // Anti-shake: penstabilan jika disokong kamera ini.
        CameraStabilization.apply(builder, provider, selector)
        val capture = builder.build()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            imageCaptureHolder.value = capture
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
        Button(
            onClick = { imageCaptureHolder.value?.let { viewModel.capture(it) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text(stringResource(R.string.capture_button))
        }
    }
}
