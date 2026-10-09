package com.example.wajuscanner.ui.scanner

import android.content.IntentSender
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wajuscanner.R
import com.example.wajuscanner.camera.ScannerLauncher
import com.example.wajuscanner.camera.ScannerResult
import com.example.wajuscanner.domain.usecase.IdCardLayout
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.wajuscanner.core.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Slot index draf kad ID: muka = 0, belakang = 1. */
private const val SLOT_FRONT = 0
private const val SLOT_BACK = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onDocumentCreated: (Long) -> Unit,
    onOpenCamera: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var idCardMode by remember { mutableStateOf(false) }
    var idCardLayout by remember { mutableStateOf(IdCardLayout.VERTICAL) }
    // Launcher didaftar melalui rememberLauncherForActivityResult — register
    // berlaku pada NavBackStackEntry (bukan Activity), jadi selamat dipanggil
    // semasa navigasi walaupun Activity sudah RESUMED.
    val activity = LocalActivity.current
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.onScanResult(ScannerLauncher.parseScanResult(activityResult))
    }
    // Import galeri TANPA ML Kit (tiada auto-crop → tiada risiko miring).
    val imageImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importImage(uri)
    }

    fun launchScan() {
        if (activity != null) {
            viewModel.startScan(activity) { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        }
    }

    fun launchRetake(slot: Int) {
        if (activity != null) {
            viewModel.retakeSlot(activity, slot) { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        }
    }

    LaunchedEffect(state) {
        if (state is ScannerUiState.Success) {
            val documentId = (state as ScannerUiState.Success).documentId
            onDocumentCreated(documentId)
            viewModel.consumeEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title)) },
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
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is ScannerUiState.Idle,
                is ScannerUiState.Launching -> {
                    ScannerIdleContent(
                        idCardMode = idCardMode,
                        idCardLayout = idCardLayout,
                        onToggleIdCardMode = {
                            idCardMode = !idCardMode
                            viewModel.setIdCardMode(idCardMode)
                        },
                        onLayoutSelected = { layout ->
                            idCardLayout = layout
                            viewModel.setIdCardLayout(layout)
                        },
                        onScanClick = { launchScan() },
                        onImportClick = {
                            imageImporter.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onOpenCamera = onOpenCamera
                    )
                }
                is ScannerUiState.Processing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.scanner_processing),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is ScannerUiState.IdCardReview -> {
                    IdCardReviewContent(
                        front = currentState.front,
                        back = currentState.back,
                        onSwap = { viewModel.swapSides() },
                        onRetakeFront = { launchRetake(SLOT_FRONT) },
                        onRetakeBack = { launchRetake(SLOT_BACK) },
                        onSave = { viewModel.saveIdCardDocument() },
                        onCancel = {
                            // Cancel review: buang draf, kembali ke skrin idle.
                            viewModel.consumeEvent()
                        }
                    )
                }
                is ScannerUiState.Error -> {
                    ErrorContent(
                        message = currentState.message,
                        onRetry = { launchScan() },
                        onBack = onNavigateBack
                    )
                }
                is ScannerUiState.Success -> {
                    // Navigation handled by LaunchedEffect; show nothing here.
                }
                else -> { }
            }
        }
    }
}

@Composable
private fun ScannerIdleContent(
    idCardMode: Boolean,
    idCardLayout: IdCardLayout,
    onToggleIdCardMode: () -> Unit,
    onLayoutSelected: (IdCardLayout) -> Unit,
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Panggung imbasan — kad besar bersempadan tong, gaya mockup Stitch.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = "PENGESANAN AUTO AKTIF",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.scanner_ready_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Mod Kad ID — kad permukaan tersendiri.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.scanner_id_mode_label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = idCardMode, onCheckedChange = { onToggleIdCardMode() })
                }
                if (idCardMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.scanner_id_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Susunan Halaman",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    ) {
                        IdCardLayout.entries.forEach { layout ->
                            FilterChip(
                                selected = idCardLayout == layout,
                                onClick = { onLayoutSelected(layout) },
                                label = {
                                    Text(
                                        text = stringResource(
                                            when (layout) {
                                                IdCardLayout.VERTICAL -> R.string.scanner_id_layout_vertical
                                                IdCardLayout.HORIZONTAL -> R.string.scanner_id_layout_horizontal
                                            }
                                        )
                                    )
                                }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.scanner_id_naming_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onScanClick, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.scanner_button_start))
        }
        OutlinedButton(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scanner_button_import))
        }
        OutlinedButton(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scanner_button_camera))
        }
    }
}

/**
 * Skrin semakan 2-slot: dua sisi kad ditayang berlabel, user boleh tukar,
 * imbas semula satu sisi, simpan, atau batal. Tiada apa-apa disimpan lagi.
 */
@Composable
private fun IdCardReviewContent(
    front: String,
    back: String,
    onSwap: () -> Unit,
    onRetakeFront: () -> Unit,
    onRetakeBack: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val frontBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = front) {
        value = withContext(Dispatchers.IO) {
            ImageUtils.loadBitmapFromUri(context, Uri.parse(front), 1024)
        }
    }
    val backBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = back) {
        value = withContext(Dispatchers.IO) {
            ImageUtils.loadBitmapFromUri(context, Uri.parse(back), 1024)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.scanner_id_review_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        ReviewSideCard(
            slot = SLOT_FRONT,
            bitmap = frontBitmap,
            onRetake = onRetakeFront
        )
        OutlinedButton(onClick = onSwap) {
            Icon(Icons.Default.SwapVert, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.scanner_id_review_swap))
        }
        ReviewSideCard(
            slot = SLOT_BACK,
            bitmap = backBitmap,
            onRetake = onRetakeBack
        )
        Spacer(Modifier.height(16.dp))
        Row {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.scanner_id_review_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave) {
                Text(stringResource(R.string.scanner_id_review_save))
            }
        }
    }
}

@Composable
private fun ReviewSideCard(
    slot: Int,
    bitmap: android.graphics.Bitmap?,
    onRetake: () -> Unit,
) {
    val sideLabel = stringResource(
        if (slot == SLOT_FRONT) R.string.id_side_front else R.string.id_side_back
    )
    val retakeLabel = stringResource(
        if (slot == SLOT_FRONT) {
            R.string.scanner_id_review_retake_front
        } else {
            R.string.scanner_id_review_retake_back
        }
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(modifier = Modifier.fillMaxWidth(0.9f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.59f), // kad kredit ≈ 85.6 × 54 mm
                contentAlignment = Alignment.Center
            ) {
                val imageBitmap = bitmap?.asImageBitmap()
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = sideLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = stringResource(R.string.scanner_id_review_loading_thumbs))
                }
            }
        }
        TextButton(onClick = onRetake) {
            Text(retakeLabel)
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.scanner_button_retry))
        }
        Button(onClick = onBack) {
            Text(stringResource(R.string.scanner_button_back))
        }
    }
}
