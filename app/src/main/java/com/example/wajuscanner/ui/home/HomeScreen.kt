package com.example.wajuscanner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.wajuscanner.R
import com.example.wajuscanner.domain.model.Document
import com.example.wajuscanner.domain.model.Page
import com.example.wajuscanner.domain.repository.DocumentRepository
import com.example.wajuscanner.ui.theme.InkOnSurface
import com.example.wajuscanner.ui.theme.InkOnSurfaceVariant
import com.example.wajuscanner.ui.theme.WajuScannerTheme
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onQrScanClick: () -> Unit,
    onIdPhotoClick: () -> Unit,
    onResumeDraft: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val draft = when (val s = state) {
        is HomeUiState.Success -> s.mostRecentDraft
        is HomeUiState.Empty -> s.mostRecentDraft
        else -> null
    }
    val documentCount = (state as? HomeUiState.Success)?.documents?.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onQrScanClick) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Imbas QR",
                        )
                    }
                    IconButton(onClick = onIdPhotoClick) {
                        Icon(
                            imageVector = Icons.Filled.Portrait,
                            contentDescription = "Foto Pasport",
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_scan_document)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            HomeHeroCard()
            Spacer(modifier = Modifier.height(12.dp))
            QuickActionGrid(
                hasDraft = draft != null,
                onScanClick = onScanClick,
                onQrScanClick = onQrScanClick,
                onIdPhotoClick = onIdPhotoClick,
                onResumeDraft = { draft?.id?.let(onResumeDraft) },
                onSettingsClick = onSettingsClick,
            )
            Spacer(modifier = Modifier.height(12.dp))
            DocumentsFolderCard(
                documentCount = documentCount,
                onClick = onOpenLibrary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            draft?.let { current ->
                ResumeDraftBanner(
                    draft = current,
                    onResume = { onResumeDraft(current.id) },
                    onDiscard = { viewModel.deleteDraft(current.id) },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Single entry point to the document library. Documents themselves live on the
 * dedicated "Dokumen Saya" screen — never listed here next to the app tiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsFolderCard(
    documentCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = InkOnSurface,
                )
                Text(
                    text = if (documentCount == null) {
                        stringResource(R.string.library_empty_title)
                    } else {
                        stringResource(R.string.library_count, documentCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkOnSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = InkOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResumeDraftBanner(
    draft: Document,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Imbasan belum selesai",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = draft.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDiscard) { Text("Buang") }
                Button(onClick = onResume) { Text("Sambung") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    WajuScannerTheme {
        HomeScreen(
            viewModel = previewHomeViewModel(),
            onScanClick = {},
            onSettingsClick = {},
            onQrScanClick = {},
            onIdPhotoClick = {},
            onResumeDraft = {},
            onOpenLibrary = {},
        )
    }
}

@Composable
private fun previewHomeViewModel(): HomeViewModel {
    val repository = remember { PreviewDocumentRepository() }
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return HomeViewModel(repository) as T
            }
        }
    )
}

private class PreviewDocumentRepository : DocumentRepository {
    override fun observeDocuments() = flowOf(emptyList<Document>())
    override fun searchDocuments(query: String) = flowOf(emptyList<Document>())
    override fun observeDrafts() = flowOf(emptyList<Document>())
    override suspend fun getMostRecentDraft(): Document? = null
    override suspend fun getDocumentById(id: Long): Document? = null
    override suspend fun getPagesForDocument(documentId: Long) = emptyList<Page>()
    override suspend fun createDocument(name: String) = 1L
    override suspend fun renameDocument(id: Long, newName: String) = true
    override suspend fun deleteDocument(id: Long) = true
    override suspend fun markAsExported(id: Long) = true
    override suspend fun searchIncludingOcr(query: String): List<Document> = emptyList()
}

// ---- Hero + tindakan pantas (Stitch Redesign 2026) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeHeroCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dokumen anda sedia diimbas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionGrid(
    hasDraft: Boolean,
    onScanClick: () -> Unit,
    onQrScanClick: () -> Unit,
    onIdPhotoClick: () -> Unit,
    onResumeDraft: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                label = "Imbas Dokumen",
                hint = "Kamera menjadi PDF",
                icon = Icons.Default.DocumentScanner,
                tint = MaterialTheme.colorScheme.primaryContainer,
                onClick = onScanClick,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                label = "Imbas Kod QR",
                hint = "Kamera dan galeri",
                icon = Icons.Default.QrCodeScanner,
                tint = MaterialTheme.colorScheme.primaryContainer,
                onClick = onQrScanClick,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                label = "Foto Rasmi",
                hint = "Pasport 35×50 mm",
                icon = Icons.Filled.Portrait,
                tint = MaterialTheme.colorScheme.primaryContainer,
                onClick = onIdPhotoClick,
                modifier = Modifier.weight(1f),
            )
            if (hasDraft) {
                ActionTile(
                    label = "Draf Tertunggak",
                    hint = "Sambung imbasan lepas",
                    icon = Icons.Default.History,
                    tint = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onResumeDraft,
                    modifier = Modifier.weight(1f),
                )
            } else {
                ActionTile(
                    label = "Tetapan",
                    hint = "Tema, fail dan lain-lain",
                    icon = Icons.Default.Menu,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTile(
    label: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .height(136.dp)
            .then(modifier),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(color = tint, shape = MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = InkOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = InkOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
