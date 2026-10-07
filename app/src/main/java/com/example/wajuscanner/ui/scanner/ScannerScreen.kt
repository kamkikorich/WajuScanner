package com.example.wajuscanner.ui.scanner

import android.content.IntentSender
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wajuscanner.R
import com.example.wajuscanner.camera.ScannerLauncher
import com.example.wajuscanner.camera.ScannerResult
import com.example.wajuscanner.domain.usecase.IdCardLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onDocumentCreated: (Long) -> Unit
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

    fun launchScan() {
        if (activity != null) {
            viewModel.startScan(activity) { intentSender ->
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
                        onScanClick = { launchScan() }
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
    onScanClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.scanner_ready_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.scanner_ready_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.scanner_id_mode_label),
                modifier = Modifier.weight(1f)
            )
            Switch(checked = idCardMode, onCheckedChange = { onToggleIdCardMode() })
        }
        if (idCardMode) {
            Text(
                text = stringResource(R.string.scanner_id_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
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
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onScanClick) {
            Text(stringResource(R.string.scanner_button_start))
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
