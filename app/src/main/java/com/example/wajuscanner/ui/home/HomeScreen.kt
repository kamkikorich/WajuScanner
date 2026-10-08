package com.example.wajuscanner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.example.wajuscanner.ui.theme.WajuScannerTheme
import kotlinx.coroutines.flow.flowOf
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScanClick: () -> Unit,
    onDocumentClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onQrScanClick: () -> Unit,
    onIdPhotoClick: () -> Unit,
    onResumeDraft: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
        ) {
            when (val currentState = state) {
                is HomeUiState.Loading -> LoadingContent()
                is HomeUiState.Empty -> {
                    currentState.mostRecentDraft?.let { draft ->
                        ResumeDraftBanner(
                            draft = draft,
                            onResume = { onResumeDraft(draft.id) },
                            onDiscard = { viewModel.deleteDraft(draft.id) },
                        )
                    }
                    EmptyContent(onScanClick = onScanClick)
                }
                is HomeUiState.Success -> {
                    currentState.mostRecentDraft?.let { draft ->
                        ResumeDraftBanner(
                            draft = draft,
                            onResume = { onResumeDraft(draft.id) },
                            onDiscard = { viewModel.deleteDraft(draft.id) },
                        )
                    }
                    DocumentListContent(
                        state = currentState,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        onDocumentClick = onDocumentClick,
                        onRename = viewModel::renameDocument,
                        onDeleteClick = viewModel::deleteDocument
                    )
                }
                is HomeUiState.Error -> ErrorContent(message = currentState.message)
            }
        }
    }
}

@Composable
private fun EmptyContent(onScanClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun ResumeDraftBanner(
    draft: com.example.wajuscanner.domain.model.Document,
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
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDiscard) { Text("Buang") }
                Button(onClick = onResume) { Text("Sambung") }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentListContent(
    state: HomeUiState.Success,
    onSearchQueryChange: (String) -> Unit,
    onDocumentClick: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDeleteClick: (Long) -> Unit
) {
    var renamingDocument by remember { mutableStateOf<Document?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = state.query,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(stringResource(R.string.home_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = state.documents,
                key = { it.id }
            ) { document ->
                DocumentCard(
                    document = document,
                    onClick = { onDocumentClick(document.id) },
                    onRenameClick = { renamingDocument = document },
                    onDeleteClick = { onDeleteClick(document.id) }
                )
            }
        }
    }

    renamingDocument?.let { document ->
        RenameDialog(
            currentName = document.name,
            onDismiss = { renamingDocument = null },
            onConfirm = { newName ->
                onRename(document.id, newName)
                renamingDocument = null
            }
        )
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.rename_hint)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && text != currentName
            ) {
                Text(stringResource(R.string.rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rename_cancel))
            }
        }
    )
}

@Composable
private fun DocumentCard(
    document: Document,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = document.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.home_document_meta,
                    document.pageCount,
                    document.createdAt.toString()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onRenameClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.cd_rename_document)
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete_document)
                        )
                    }
                }
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
            onDocumentClick = {},
            onSettingsClick = {},
            onQrScanClick = {},
            onIdPhotoClick = {},
            onResumeDraft = {},
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
