package com.example.wajuscanner.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wajuscanner.R
import com.example.wajuscanner.domain.model.DocumentFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    pageId: Long,
    viewModel: EditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(pageId) {
        viewModel.loadPage(pageId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::rotateRight) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.editor_rotate)
                        )
                    }
                }
            )
        },
        bottomBar = {
            when (val currentState = state) {
                is EditorUiState.Edit -> {
                    EditorBottomBar(
                        selectedFilter = currentState.filter,
                        isSaving = currentState.isSaving,
                        onFilterSelected = viewModel::setFilter,
                        onSaveClick = {
                            viewModel.saveEdits(
                                pageId = pageId,
                                onSaved = onSaveComplete,
                                onError = { /* TODO: show snackbar in next iteration */ }
                            )
                        }
                    )
                }
                else -> { }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is EditorUiState.Loading -> CircularProgressIndicator()
                is EditorUiState.Error -> Text(
                    text = currentState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                is EditorUiState.Edit -> {
                    Image(
                        bitmap = currentState.bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.editor_image_preview),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorBottomBar(
    selectedFilter: DocumentFilter,
    isSaving: Boolean,
    onFilterSelected: (DocumentFilter) -> Unit,
    onSaveClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(DocumentFilter.entries) { filter ->
                FilterChip(
                    selected = filter == selectedFilter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.displayName) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSaveClick,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.editor_button_save))
            }
        }
    }
}
