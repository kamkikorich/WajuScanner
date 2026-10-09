package com.example.wajuscanner.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.wajuscanner.core.ai.AiAction

/**
 * Helaian AI opsyenal pada skrin Detail dokumen. Hanya dipaparkan apabila
 * pengguna telah mengaktifkan AI + menyimpan kunci. Ralat dipaparkan di sini
 * sahaja dan tidak menjejaskan aliran scan/simpan/eksport.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(
    state: AiUiState,
    imageSupported: Boolean,
    onRun: (AiAction, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var action by remember { mutableStateOf(AiAction.SUMMARIZE) }
    var question by remember { mutableStateOf("") }
    var useImage by remember { mutableStateOf(false) }
    val loading = state is AiUiState.Loading

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI Dokumen", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Teks dokumen dihantar ke pembekal AI pilihan anda, menggunakan kunci anda sendiri.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = action == AiAction.SUMMARIZE,
                    onClick = { action = AiAction.SUMMARIZE },
                    label = { Text("Ringkaskan") },
                )
                FilterChip(
                    selected = action == AiAction.EXTRACT_FIELDS,
                    onClick = { action = AiAction.EXTRACT_FIELDS },
                    label = { Text("Ekstrak Medan") },
                )
                FilterChip(
                    selected = action == AiAction.ASK,
                    onClick = { action = AiAction.ASK },
                    label = { Text("Tanya") },
                )
            }

            if (action == AiAction.ASK) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Soalan tentang dokumen") },
                    minLines = 2,
                )
            }

            if (imageSupported) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sertakan imej halaman", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Perlu model vision. Halaman pertama dihantar bersama teks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = useImage, onCheckedChange = { useImage = it })
                }
            }

            Button(
                onClick = { onRun(action, question.takeIf { it.isNotBlank() }, useImage && imageSupported) },
                enabled = !loading && (action != AiAction.ASK || question.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Jalankan")
                }
            }

            when (state) {
                is AiUiState.Result -> {
                    Text(state.text, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { copyToClipboard(context, state.text) }) { Text("Salin") }
                        TextButton(onClick = { shareAiResult(context, state.text) }) { Text("Kongsi") }
                    }
                }
                is AiUiState.Error -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AI", text))
}

private fun shareAiResult(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Kongsi hasil AI"))
}
