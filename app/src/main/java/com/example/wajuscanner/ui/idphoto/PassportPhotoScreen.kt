package com.example.wajuscanner.ui.idphoto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Skrin "Foto Pasport": kamera dengan garis panduan oval 35:50, pemerosesan
 * latar putih dan hasil boleh disimpan (PNG) atau dicetak (helaian A4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportPhotoScreen(
    viewModel: PassportPhotoViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foto Pasport") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
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
            when (val current = state) {
                IdPhotoUiState.Camera -> {
                    if (hasPermission) {
                        CameraCaptureContent(viewModel = viewModel)
                    } else {
                        PermissionRationale(
                            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        )
                    }
                }

                IdPhotoUiState.Processing -> ProcessingContent()

                is IdPhotoUiState.Result -> ResultContent(
                    photo = current.photo,
                    onSavePng = { viewModel.savePng(context) },
                    onSheet = {
                        viewModel.createPrintSheet(context) { uri ->
                            sharePrintSheet(context, uri)
                        }
                    },
                    onRetake = viewModel::retake,
                )
            }

            message?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Preview kamera (CameraX) dengan overlay panduan oval 35:50. Tangkapan
 * ditetapkan pada resolusi paling tinggi yang sedia ada melalui
 * [ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY].
 */
@Composable
private fun CameraCaptureContent(viewModel: PassportPhotoViewModel) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build(),
            )
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    } catch (_: Exception) {
                        // Kamera tidak tersedia pada peranti ini.
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
        )
        GuideOverlay(modifier = Modifier.fillMaxSize())
        Button(
            onClick = { takePhoto(context, imageCapture, viewModel) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text("Ambil Foto")
        }
    }
}

/** Tangkap sekali; keputusan diuruskan melalui [viewModel.onCaptured]. */
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    viewModel: PassportPhotoViewModel,
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                viewModel.onCaptured(image)
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.reportError("Gagal mengambil foto: ${exception.localizedMessage}")
            }
        },
    )
}

/**
 * Garis panduan oval (nisbah 35:50 — MyKad/pasport) di tengah viewfinder +
 * Arahan pendek. Borde putih 3 dp atas bentuk oval sebenar (bukan stadium).
 */
@Composable
private fun GuideOverlay(modifier: Modifier) {
    BoxWithConstraints(modifier = modifier) {
        var guideHeight = maxHeight * 0.58f
        var guideWidth = guideHeight * GUIDE_RATIO
        val maxGuideWidth = maxWidth * 0.9f
        if (guideWidth > maxGuideWidth) {
            guideWidth = maxGuideWidth
            guideHeight = guideWidth / GUIDE_RATIO
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(guideWidth)
                    .height(guideHeight)
                    .border(width = 3.dp, color = Color.White, shape = OvalGuideShape),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Letakkan kepala dalam bulatan. Latar belakang putih diganti secara automatik.",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Bentuk oval sebenar; border Compose menerima sebarang [Shape]. */
private val OvalGuideShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(
        Path().apply {
            val w = size.width
            val h = size.height
            val centerX = w / 2f
            val centerY = h / 2f
            val kappa = 0.5522847f
            val kx = centerX * kappa
            val ky = centerY * kappa
            moveTo(centerX, 0f)
            cubicTo(centerX + kx, 0f, w, centerY - ky, w, centerY)
            cubicTo(w, centerY + ky, centerX + kx, h, centerX, h)
            cubicTo(centerX - kx, h, 0f, centerY + ky, 0f, centerY)
            cubicTo(0f, centerY - ky, centerX - kx, 0f, centerX, 0f)
            close()
        },
    )
}

@Composable
private fun ProcessingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text("Memproses foto...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Hasil akhir: prakeluaran portret 35:50 (Fit, lebar maksimum 0.9) + tiga
 * tindakan: simpan PNG, jana/kongsi helaian cetak, imbas semula.
 */
@Composable
private fun ResultContent(
    photo: Bitmap,
    onSavePng: () -> Unit,
    onSheet: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Foto Pasport Sedia",
            style = MaterialTheme.typography.titleLarge,
        )
        Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = "Prakeluaran foto pasport",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .weight(1f, fill = false),
        )
        Button(
            onClick = onSavePng,
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Text("Simpan PNG")
        }
        Button(
            onClick = onSheet,
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Text("Helaian Cetak")
        }
        OutlinedButton(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Text("Imbas Semula")
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
            text = "Kamera diperlukan untuk mengambil foto pasport.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Beri Kebenaran") }
    }
}

/**
 * Kongsi helaian cetak PNG melalui ACTION_SEND dengan FileProvider content
 * URI (match polisi DocumentDetailViewModel/ExportDocumentUseCase):
 * FLAG_GRANT_READ_URI_PERMISSION atas chooser supaya penerima boleh membaca.
 */
private fun sharePrintSheet(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
    }
    context.startActivity(
        Intent.createChooser(intent, "Kongsi Helaian Cetak").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )
}

/** Nisbah panduan: 35 mm (lebar) : 50 mm (tinggi). */
private const val GUIDE_RATIO = 35f / 50f
