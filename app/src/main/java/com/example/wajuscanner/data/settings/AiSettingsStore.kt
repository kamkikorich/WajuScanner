package com.example.wajuscanner.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wajuscanner.core.security.SecretCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

enum class AiProviderType { GEMINI, OPENAI_COMPATIBLE }

data class AiSettings(
    val enabled: Boolean = false,
    val provider: AiProviderType = AiProviderType.GEMINI,
    val model: String = "",
    val baseUrl: String = "",
    val hasKey: Boolean = false,
)

/**
 * Simpanan tetapan AI + kunci API (disulitkan). AI sepenuhnya opsyenal: jika
 * tiada kunci, [hasKey] palsu dan ciri AI senyap sepenuhnya.
 */
@Singleton
class AiSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: SecretCipher,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val PROVIDER = stringPreferencesKey("provider")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
        val KEY_ENC = stringPreferencesKey("api_key_enc")
    }

    val settings: Flow<AiSettings> = context.aiDataStore.data.map { prefs ->
        AiSettings(
            enabled = prefs[Keys.ENABLED] ?: false,
            provider = prefs[Keys.PROVIDER]
                ?.let { runCatching { AiProviderType.valueOf(it) }.getOrNull() }
                ?: AiProviderType.GEMINI,
            model = prefs[Keys.MODEL].orEmpty(),
            baseUrl = prefs[Keys.BASE_URL].orEmpty(),
            hasKey = !prefs[Keys.KEY_ENC].isNullOrBlank(),
        )
    }

    suspend fun save(enabled: Boolean, provider: AiProviderType, model: String, baseUrl: String) {
        context.aiDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = enabled
            prefs[Keys.PROVIDER] = provider.name
            prefs[Keys.MODEL] = model.trim()
            prefs[Keys.BASE_URL] = baseUrl.trim()
        }
    }

    /** Simpan kunci baharu (disulitkan). */
    suspend fun saveKey(apiKey: String) {
        val encrypted = cipher.encrypt(apiKey.trim())
        context.aiDataStore.edit { it[Keys.KEY_ENC] = encrypted }
    }

    /** Kunci plaintext untuk panggilan AI; null jika tiada / gagal dinyahsulit. */
    suspend fun apiKey(): String? {
        val encrypted = context.aiDataStore.data.first()[Keys.KEY_ENC] ?: return null
        return cipher.decrypt(encrypted)
    }

    /** Fungsi Padam: buang kunci + semua tetapan AI (kembali senyap). */
    suspend fun clear() {
        context.aiDataStore.edit { it.clear() }
    }
}
