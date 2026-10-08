package com.example.wajuscanner.ui.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    viewModel: QrViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val cameraHolder = remember { CameraHolder() }
    var flashOn by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.decodeFromImage(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Imbas QR") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "Imbas dari galeri",
                        )
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Sejarah imbasan",
                        )
                    }
                    IconButton(onClick = {
                        cameraHolder.camera?.cameraControl?.enableTorch(!flashOn)
                        flashOn = !flashOn
                    }) {
                        Icon(
                            imageVector = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = if (flashOn) "Matikan lampu" else "Hidupkan lampu",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (hasPermission) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalCameraController provides cameraHolder.camera,
                ) {
                    CameraViewfinder(
                        viewModel = viewModel,
                        holder = cameraHolder,
                    )
                    ScannerOverlay(viewModel = viewModel)
                    ResultListener(viewModel = viewModel)
                    ZoomIndicator(viewModel = viewModel)
                }
            } else {
                PermissionRationale(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }

    if (showHistory) {
        QrHistorySheet(
            viewModel = viewModel,
            onDismiss = { showHistory = false },
        )
    }
}

private class CameraHolder(var camera: Camera? = null)

/**
 * Floats the current zoom level above the preview and reacts to ML Kit zoom
 * suggestions. The camera control reference is captured in [CameraViewfinder]
 * via [LocalCameraController] so this composable can drive [setZoomRatio]
 * without rebuilding the preview.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ZoomIndicator(viewModel: QrViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val camera = LocalCameraController.current
    var currentRatio by remember { mutableStateOf(1f) }

    LaunchedEffect(state) {
        val suggestion = state as? QrUiState.ZoomSuggested ?: return@LaunchedEffect
        val clamped = suggestion.ratio.coerceIn(1f, MAX_ZOOM)
        camera?.cameraControl?.setZoomRatio(clamped)
        currentRatio = clamped
        viewModel.consumeZoomSuggestion()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "%.1fx".format(currentRatio),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
        if (currentRatio > 1.01f) {
            FilledTonalIconButton(
                onClick = {
                    camera?.cameraControl?.setZoomRatio(1f)
                    currentRatio = 1f
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                ),
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Set semula zum",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private const val MAX_ZOOM = 4f

/**
 * Holder passed from [CameraViewfinder] to its overlay so the overlay can
 * invoke camera control without keeping a second camera reference.
 */
private val LocalCameraController = androidx.compose.runtime.compositionLocalOf<Camera?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraViewfinder(viewModel: QrViewModel, holder: CameraHolder) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val analyzer = remember(viewModel) { viewModel.analyzer() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(ctx.mainExecutor, analyzer) }

                try {
                    provider.unbindAll()
                    holder.camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (_: Exception) {
                    // Camera unavailable on this device; parent shows fallback.
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}

@Composable
private fun ScannerOverlay(viewModel: QrViewModel) {
    val boxes by viewModel.detectedBoxes.collectAsStateWithLifecycle()
    val imageInfo by viewModel.imageInfo.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        boxes.forEach { imageRect ->
            val projected = projectBoundingBox(
                imageRect = imageRect,
                imageInfo = imageInfo,
                previewWidthPx = constraints.maxWidth,
                previewHeightPx = constraints.maxHeight,
            )
            Box(
                modifier = Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            projected.left.toInt(),
                            projected.top.toInt(),
                        )
                    }
                    .size(
                        width = with(androidx.compose.ui.platform.LocalDensity.current) {
                            projected.width().toDp()
                        },
                        height = with(androidx.compose.ui.platform.LocalDensity.current) {
                            projected.height().toDp()
                        },
                    )
                    .border(
                        width = 3.dp,
                        color = Color(0xFF34C759),
                        shape = RoundedCornerShape(12.dp),
                    ),
            )
        }
    }
}

/**
 * Project an ML Kit bounding box (given in image-space coordinates) into
 * PreviewView pixel coordinates. With PreviewView.ScaleType.FILL_CENTER the
 * preview scales uniformly to fill the viewport and crops the longer axis;
 * [rotationDegrees] is added because the sensor is mounted landscape and the
 * portrait frame is the rotated image-space.
 */
private fun projectBoundingBox(
    imageRect: android.graphics.Rect,
    imageInfo: ImageInfo,
    previewWidthPx: Int,
    previewHeightPx: Int,
): android.graphics.Rect {
    val imageW = imageInfo.width.toFloat()
    val imageH = imageInfo.height.toFloat()

    // Effective buffer dims after applying sensor rotation. CameraX delivers
    // the buffer in its native orientation; rotationDegrees rotates it for
    // display. ML Kit bounding boxes use the rotated (display) coordinates.
    val (dispW, dispH) = if (imageInfo.rotationDegrees % 180 == 0) {
        imageW to imageH
    } else {
        imageH to imageW
    }

    // FILL_CENTER: scale uniformly to fill, center the longer axis.
    val scale = maxOf(previewWidthPx / dispW, previewHeightPx / dispH)
    val renderedW = dispW * scale
    val renderedH = dispH * scale
    val offsetX = (previewWidthPx - renderedW) / 2f
    val offsetY = (previewHeightPx - renderedH) / 2f

    val rotatedRect = if (imageInfo.rotationDegrees % 180 == 0) {
        imageRect
    } else {
        // Swap x/y when rotated 90 or 270 degrees.
        android.graphics.Rect(
            imageRect.top,
            imageInfo.width - imageRect.bottom,
            imageRect.bottom,
            imageInfo.width - imageRect.top,
        )
    }

    return android.graphics.Rect(
        (offsetX + rotatedRect.left * scale).toInt(),
        (offsetY + rotatedRect.top * scale).toInt(),
        (offsetX + rotatedRect.right * scale).toInt(),
        (offsetY + rotatedRect.bottom * scale).toInt(),
    )
}

@Composable
private fun ResultListener(viewModel: QrViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state is QrUiState.Found) {
        FoundDialog(
            value = (state as QrUiState.Found).value,
            onScanAgain = viewModel::resumeScanning,
        )
    }
    if (state is QrUiState.DecodeFailed) {
        val message = (state as QrUiState.DecodeFailed).message
        AlertDialog(
            onDismissRequest = viewModel::resumeScanning,
            title = { Text("Imbas dari imej gagal") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::resumeScanning) { Text("OK") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoundDialog(
    value: String,
    onScanAgain: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onScanAgain,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "QR Dijumpai",
                style = MaterialTheme.typography.titleLarge,
            )
            if (isLikelyUrl(value)) {
                UrlLinkText(value)
            } else {
                Text(
                    text = value,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { shareText(context, value) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kongsi")
                }
                Button(
                    onClick = { copyToClipboard(context, value) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salin")
                }
            }
            TextButton(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Imbas Lagi")
            }
        }
    }
}

/**
 * Sejarah imbasan (CamScanner parity): hasil kod tidak hilang lagi dahulu —
 * senarai terkini boleh disalin/dikongsi semula, dipadam satu-satu atau
 * dikosongkan sepenuhnya.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrHistorySheet(
    viewModel: QrViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFormat = remember { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sejarah Imbasan",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = viewModel::clearHistory) {
                    Text("Kosongkan")
                }
            }
        }
        if (history.isEmpty()) {
            Text(
                text = "Tiada rekod lagi. Kod yang diimbas akan tersimpan di sini.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(history, key = { it.id }) { entry ->
                    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.content,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = timeFormat.format(java.util.Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { copyToClipboard(context, entry.content) }) {
                                Text("Salin")
                            }
                            IconButton(onClick = { viewModel.deleteHistoryEntry(entry.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Padam rekod")
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Kamera diperlukan untuk mengimbas kod QR.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Beri Kebenaran") }
    }
}

private fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("QR", value))
}

private fun shareText(context: Context, value: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, value)
    }
    context.startActivity(Intent.createChooser(intent, "Kongsi QR"))
}

/**
 * Heuristic: a value is rendered as a clickable URL when it starts with
 * http:// or https:// and parses as an absolute URL.
 */
private fun isLikelyUrl(value: String): Boolean {
    val trimmed = value.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) &&
        !trimmed.startsWith("https://", ignoreCase = true)
    ) return false
    return runCatching { android.net.Uri.parse(trimmed) }
        .map { it.scheme?.startsWith("http") == true && !it.host.isNullOrBlank() }
        .getOrDefault(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlLinkText(url: String) {
    val uriHandler = LocalUriHandler.current
    val link = LinkAnnotation.Url(url) { uriHandler.openUri(url) }
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        addLink(link, 0, url.length)
        append(url)
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.primary,
    )
}