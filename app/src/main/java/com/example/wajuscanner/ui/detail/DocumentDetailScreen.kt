package com.example.wajuscanner.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wajuscanner.R
import com.example.wajuscanner.core.util.FileUtils
import com.example.wajuscanner.domain.model.PdfCompressionMode
import com.example.wajuscanner.domain.model.PdfOptions
import com.example.wajuscanner.domain.model.PdfOrientation
import com.example.wajuscanner.domain.model.PdfPageSize
import com.example.wajuscanner.domain.usecase.DocumentExportState
import com.example.wajuscanner.ui.preview.PageCard
import com.example.wajuscanner.ui.preview.PreviewUiState
import com.example.wajuscanner.ui.preview.PreviewViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: Long,
    viewModel: PreviewViewModel = hiltViewModel(),
    exportViewModel: DocumentDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onEditPage: (Long) -> Unit,
    onAddPage: () -> Unit,
    onOcrPage: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by exportViewModel.exportState.collectAsStateWithLifecycle()
    val pdfOptions by exportViewModel.pdfOptions.collectAsStateWithLifecycle()
    val aiAvailable by exportViewModel.aiAvailable.collectAsStateWithLifecycle()
    val aiState by exportViewModel.aiState.collectAsStateWithLifecycle()
    val aiImageSupported by exportViewModel.aiImageSupported.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAi by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is DocumentExportState.ReadyToShare -> {
                context.startActivity(s.intent)
                exportViewModel.consumeExportEvent()
            }
            is DocumentExportState.Saved -> {
                snackbarHostState.showSnackbar("Saved: ${s.fileName}")
                exportViewModel.consumeExportEvent()
            }
            is DocumentExportState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                exportViewModel.consumeExportEvent()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (aiAvailable) {
                        IconButton(onClick = { showAi = true }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.ai_title)
                            )
                        }
                    }
                    IconButton(
                        onClick = { exportViewModel.exportAndShare(documentId) },
                        enabled = state is PreviewUiState.Success &&
                            (state as PreviewUiState.Success).pages.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.detail_share)
                        )
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        enabled = state is PreviewUiState.Success &&
                            (state as PreviewUiState.Success).pages.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = stringResource(R.string.export_title)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPage) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.detail_add_page)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val currentState = state) {
                is PreviewUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is PreviewUiState.Error -> Text(
                    text = currentState.message,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
                is PreviewUiState.Success -> {
                    DetailContent(
                        state = currentState,
                        onEditPage = onEditPage,
                        onDeletePage = viewModel::deletePage,
                        onDuplicatePage = viewModel::duplicatePage,
                        onOcrPage = onOcrPage,
                        onRotatePage = viewModel::rotatePage
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        ExportOptionsDialog(
            options = pdfOptions,
            isExporting = exportState is DocumentExportState.Exporting,
            onPageSizeChange = exportViewModel::setPageSize,
            onOrientationChange = exportViewModel::setOrientation,
            onCompressionChange = exportViewModel::setCompression,
            onDismiss = { showExportDialog = false },
            onShare = {
                showExportDialog = false
                exportViewModel.exportAndShare(documentId)
            },
            onSave = {
                showExportDialog = false
                exportViewModel.exportAndSave(documentId)
            }
        )
    }

    if (showAi) {
        AiSheet(
            state = aiState,
            imageSupported = aiImageSupported,
            onRun = { action, question, useImage ->
                exportViewModel.runAiAction(documentId, action, question, useImage)
            },
            onDismiss = {
                showAi = false
                exportViewModel.resetAi()
            },
        )
    }
}

@Composable
private fun ExportOptionsDialog(
    options: PdfOptions,
    isExporting: Boolean,
    onPageSizeChange: (PdfPageSize) -> Unit,
    onOrientationChange: (PdfOrientation) -> Unit,
    onCompressionChange: (PdfCompressionMode) -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_page_size), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfPageSize.entries.forEach { size ->
                        FilterChip(
                            selected = options.pageSize == size,
                            onClick = { onPageSizeChange(size) },
                            label = { Text(size.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text(stringResource(R.string.export_orientation), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfOrientation.entries.forEach { o ->
                        FilterChip(
                            selected = options.orientation == o,
                            onClick = { onOrientationChange(o) },
                            label = { Text(o.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text(stringResource(R.string.export_compression), style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PdfCompressionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = options.compression == mode,
                            onClick = { onCompressionChange(mode) },
                            label = {
                                Text(
                                    text = stringResource(
                                        when (mode) {
                                            PdfCompressionMode.FULL -> R.string.export_compression_full
                                            PdfCompressionMode.COMPRESS -> R.string.export_compression_compress
                                            PdfCompressionMode.COMPRESS_GRAYSCALE -> R.string.export_compression_grayscale
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
                if (isExporting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text(stringResource(R.string.export_generating))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onShare, enabled = !isExporting) {
                Text(stringResource(R.string.export_share))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onSave, enabled = !isExporting) {
                Text(stringResource(R.string.export_save))
            }
        }
    )
}

@Composable
private fun DetailContent(
    state: PreviewUiState.Success,
    onEditPage: (Long) -> Unit,
    onDeletePage: (Long) -> Unit,
    onDuplicatePage: (Long) -> Unit,
    onOcrPage: (Long) -> Unit,
    onRotatePage: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = state.document.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            text = stringResource(
                R.string.detail_meta,
                state.pages.size,
                FileUtils.formatFileSize(totalPageSizeBytes(state.pages.map { it.imagePath }))
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(state.pages, key = { _, page -> page.id }) { index, page ->
                PageCard(
                    page = page,
                    pageNumber = index + 1,
                    onEdit = { onEditPage(page.id) },
                    onDelete = { onDeletePage(page.id) },
                    onDuplicate = { onDuplicatePage(page.id) },
                    onOcr = { onOcrPage(page.id) },
                    onRotate = { onRotatePage(page.id) }
                )
            }
        }
    }
}

/** Sums file sizes for the given absolute image paths (missing files count as 0). */
private fun totalPageSizeBytes(paths: List<String>): Long =
    paths.sumOf { path -> try { File(path).length() } catch (_: Exception) { 0L } }