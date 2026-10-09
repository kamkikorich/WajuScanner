package com.example.wajuscanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wajuscanner.data.settings.AiProviderType

/**
 * Seksyen "AI (BYOK)" di skrin Tetapan. Ciri AI **opsyenal**: selagi pengguna
 * tidak mengaktifkannya dan menyimpan kunci sendiri, tiada apa-apa berubah.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsSection(viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("AI (BYOK)", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Opsyenal. Guna kunci API anda sendiri (Gemini atau OpenAI-compatible). " +
                    "Kunci disulitkan dalam peranti ini; tiada data dihantar melainkan anda jalankan tindakan AI.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aktifkan AI", modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
            }

            HorizontalDivider()

            Text("Pembekal", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.provider == AiProviderType.GEMINI,
                    onClick = { viewModel.setProvider(AiProviderType.GEMINI) },
                    label = { Text("Gemini") },
                )
                FilterChip(
                    selected = state.provider == AiProviderType.OPENAI_COMPATIBLE,
                    onClick = { viewModel.setProvider(AiProviderType.OPENAI_COMPATIBLE) },
                    label = { Text("OpenAI-compatible") },
                )
            }

            OutlinedTextField(
                value = state.keyInput,
                onValueChange = viewModel::setKeyInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (state.hasKey) "Kunci API (disimpan)" else "Kunci API") },
                singleLine = true,
                visualTransformation = if (state.showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleShowKey) {
                        Icon(
                            imageVector = if (state.showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (state.showKey) "Sembunyi kunci" else "Papar kunci",
                        )
                    }
                },
            )

            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::setModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model (pilihan)") },
                placeholder = {
                    Text(
                        if (state.provider == AiProviderType.GEMINI) "gemini-3.8-flash" else "gpt-4o-mini"
                    )
                },
                singleLine = true,
            )

            if (state.provider == AiProviderType.OPENAI_COMPATIBLE) {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::setBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL (kosong = OpenAI lalai)") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::save, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Text("Simpan")
                }
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !state.busy && (state.hasKey || state.keyInput.isNotBlank()),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Uji")
                }
            }

            if (state.hasKey || state.enabled) {
                TextButton(onClick = viewModel::deleteKey, enabled = !state.busy) {
                    Text("Padam Kunci (matikan AI)")
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
