package com.example.wajuscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.core.ai.RunAiUseCase
import com.example.wajuscanner.data.settings.AiProviderType
import com.example.wajuscanner.data.settings.AiSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiSettingsUiState(
    val enabled: Boolean = false,
    val provider: AiProviderType = AiProviderType.GEMINI,
    val model: String = "",
    val baseUrl: String = "",
    val hasKey: Boolean = false,
    val keyInput: String = "",
    val showKey: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val store: AiSettingsStore,
    private val runAi: RunAiUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AiSettingsUiState())
    val state: StateFlow<AiSettingsUiState> = _state

    init {
        viewModelScope.launch {
            store.settings.collect { s ->
                _state.update {
                    it.copy(
                        enabled = s.enabled,
                        provider = s.provider,
                        model = s.model,
                        baseUrl = s.baseUrl,
                        hasKey = s.hasKey,
                    )
                }
            }
        }
    }

    fun setEnabled(value: Boolean) = _state.update { it.copy(enabled = value) }
    fun setProvider(value: AiProviderType) = _state.update { it.copy(provider = value) }
    fun setModel(value: String) = _state.update { it.copy(model = value) }
    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value) }
    fun setKeyInput(value: String) = _state.update { it.copy(keyInput = value) }
    fun toggleShowKey() = _state.update { it.copy(showKey = !it.showKey) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            store.save(s.enabled, s.provider, s.model, s.baseUrl)
            if (s.keyInput.isNotBlank()) {
                store.saveKey(s.keyInput)
                _state.update { it.copy(keyInput = "", message = "Kunci disimpan.") }
            } else {
                _state.update { it.copy(message = "Tetapan disimpan.") }
            }
        }
    }

    fun deleteKey() {
        viewModelScope.launch {
            store.clear()
            _state.update {
                it.copy(keyInput = "", enabled = false, message = "Kunci dipadam. AI dimatikan.")
            }
        }
    }

    /** Simpan dahulu, kemudian cuba satu panggilan ringkas untuk sahkan kunci. */
    fun testConnection() {
        viewModelScope.launch {
            val s = _state.value
            store.save(s.enabled, s.provider, s.model, s.baseUrl)
            if (s.keyInput.isNotBlank()) store.saveKey(s.keyInput)
            _state.update { it.copy(busy = true, message = "Menguji sambungan…", keyInput = "") }
            runAi.testConnection().fold(
                onSuccess = { reply ->
                    _state.update { it.copy(busy = false, message = "Berjaya: ${reply.take(60)}") }
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = false, message = "Gagal: ${e.localizedMessage}") }
                },
            )
        }
    }
}
