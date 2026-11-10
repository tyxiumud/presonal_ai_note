package com.example.test0.ai.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class KeystoreAiSettingsStore(
    context: Context,
    private val preferencesName: String = "ai-settings",
    private val keyAlias: String = "personal-vault-ai-api-key",
) : AiSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
    private val mutableSettings = MutableStateFlow(readSnapshot())

    override val settings: StateFlow<AiSettingsSnapshot> = mutableSettings.asStateFlow()

    override suspend fun save(input: AiSettingsInput) = withContext(Dispatchers.IO) {
        val editor = preferences.edit()
            .putString(KEY_BASE_URL, input.baseUrl.trim())
            .putString(KEY_MODEL, input.model.trim())
            .putBoolean(KEY_TASKS, input.includeTasks)
            .putBoolean(KEY_SUGGESTIONS, input.includeSuggestions)
            .putBoolean(KEY_REFLECTIONS, input.includeReflections)
            .putString(KEY_PERSONA, input.persona.name)
            .putBoolean(KEY_ALLOW_EDITING, input.allowEditing)
            .putString(KEY_SKIN, input.skin.name)
            .putBoolean(KEY_VISION_USE_LLM, input.visionUseLlm)
            .putString(KEY_VISION_BASE_URL, input.visionBaseUrl.trim())
            .putString(KEY_VISION_MODEL, input.visionModel.trim())
            .putString(KEY_TRANSCRIPTION_BASE_URL, input.transcriptionBaseUrl.trim())
            .putString(
                KEY_TRANSCRIPTION_MODEL,
                input.transcriptionModel.trim().ifBlank { DEFAULT_TRANSCRIPTION_MODEL },
            )

        listOf(
            input.newApiKey to KeyPair(KEY_ENCRYPTED_API_KEY, KEY_API_KEY_IV),
            input.newVisionApiKey to KeyPair(KEY_ENCRYPTED_VISION_API_KEY, KEY_VISION_API_KEY_IV),
            input.newTranscriptionApiKey to KeyPair(
                KEY_ENCRYPTED_TRANSCRIPTION_API_KEY,
                KEY_TRANSCRIPTION_API_KEY_IV,
            ),
        ).forEach { (value, keys) ->
            value?.trim()?.takeIf(String::isNotEmpty)?.let { plain ->
                val encrypted = encrypt(plain)
                editor
                    .putString(keys.cipherKey, encrypted.cipherText)
                    .putString(keys.ivKey, encrypted.iv)
            }
        }

        check(editor.commit()) { "无法保存 AI 设置" }
        mutableSettings.value = readSnapshot()
    }

    override suspend fun connection(): AiConnectionSettings? = resolveLlm(readSnapshot())

    override suspend fun visionConnection(): AiConnectionSettings? = withContext(Dispatchers.IO) {
        val snapshot = readSnapshot()
        if (snapshot.visionUseLlm) {
            val llm = resolveLlm(snapshot) ?: return@withContext null
            return@withContext llm.copy(model = snapshot.visionModel.ifBlank { llm.model })
        }
        val apiKey = decryptStored(KEY_ENCRYPTED_VISION_API_KEY, KEY_VISION_API_KEY_IV)
            ?: return@withContext null
        val baseUrl = snapshot.visionBaseUrl
        val model = snapshot.visionModel
        if (baseUrl.isBlank() || model.isBlank()) return@withContext null
        AiConnectionSettings(baseUrl, model, apiKey)
    }

    override suspend fun transcriptionConnection(): AiConnectionSettings? =
        withContext(Dispatchers.IO) {
            val snapshot = readSnapshot()
            val dedicatedKey = decryptStored(
                KEY_ENCRYPTED_TRANSCRIPTION_API_KEY,
                KEY_TRANSCRIPTION_API_KEY_IV,
            )
            val baseUrl = snapshot.transcriptionBaseUrl
            // 没单独配地址或密钥时，沿用聊天连接，只换转写模型
            if (baseUrl.isBlank() || dedicatedKey == null) {
                val llm = resolveLlm(snapshot) ?: return@withContext null
                return@withContext llm.copy(model = snapshot.transcriptionModel)
            }
            AiConnectionSettings(baseUrl, snapshot.transcriptionModel, dedicatedKey)
        }

    override suspend fun clearApiKey() = clearKeys(listOf(KEY_ENCRYPTED_API_KEY, KEY_API_KEY_IV))

    override suspend fun clearVisionApiKey() =
        clearKeys(listOf(KEY_ENCRYPTED_VISION_API_KEY, KEY_VISION_API_KEY_IV))

    override suspend fun clearTranscriptionApiKey() =
        clearKeys(listOf(KEY_ENCRYPTED_TRANSCRIPTION_API_KEY, KEY_TRANSCRIPTION_API_KEY_IV))

    override suspend fun setAllowEditing(value: Boolean) = withContext(Dispatchers.IO) {
        check(preferences.edit().putBoolean(KEY_ALLOW_EDITING, value).commit()) {
            "无法保存编辑设置"
        }
        mutableSettings.value = readSnapshot()
    }

    override suspend fun setSkin(skin: AppSkin) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(KEY_SKIN, skin.name).commit()) {
            "无法保存外观设置"
        }
        mutableSettings.value = readSnapshot()
    }

    private suspend fun clearKeys(keys: List<String>) = withContext(Dispatchers.IO) {
        val editor = preferences.edit()
        keys.forEach { editor.remove(it) }
        check(editor.commit()) { "无法删除密钥" }
        mutableSettings.value = readSnapshot()
    }

    private fun resolveLlm(snapshot: AiSettingsSnapshot): AiConnectionSettings? {
        val apiKey = decryptStored(KEY_ENCRYPTED_API_KEY, KEY_API_KEY_IV) ?: return null
        if (snapshot.baseUrl.isBlank() || snapshot.model.isBlank()) return null
        return AiConnectionSettings(snapshot.baseUrl, snapshot.model, apiKey)
    }

    private fun readSnapshot(): AiSettingsSnapshot = AiSettingsSnapshot(
        baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
        model = preferences.getString(KEY_MODEL, "").orEmpty(),
        hasApiKey = decryptStored(KEY_ENCRYPTED_API_KEY, KEY_API_KEY_IV) != null,
        includeTasks = preferences.getBoolean(KEY_TASKS, true),
        includeSuggestions = preferences.getBoolean(KEY_SUGGESTIONS, true),
        includeReflections = preferences.getBoolean(KEY_REFLECTIONS, true),
        persona = readPersona(),
        allowEditing = preferences.getBoolean(KEY_ALLOW_EDITING, false),
        skin = readSkin(),
        visionUseLlm = preferences.getBoolean(KEY_VISION_USE_LLM, true),
        visionBaseUrl = preferences.getString(KEY_VISION_BASE_URL, "").orEmpty(),
        hasVisionApiKey = decryptStored(
            KEY_ENCRYPTED_VISION_API_KEY,
            KEY_VISION_API_KEY_IV,
        ) != null,
        visionModel = preferences.getString(KEY_VISION_MODEL, "").orEmpty(),
        transcriptionBaseUrl = preferences.getString(KEY_TRANSCRIPTION_BASE_URL, "").orEmpty(),
        hasTranscriptionApiKey = decryptStored(
            KEY_ENCRYPTED_TRANSCRIPTION_API_KEY,
            KEY_TRANSCRIPTION_API_KEY_IV,
        ) != null,
        transcriptionModel = preferences
            .getString(KEY_TRANSCRIPTION_MODEL, DEFAULT_TRANSCRIPTION_MODEL)
            .orEmpty()
            .ifBlank { DEFAULT_TRANSCRIPTION_MODEL },
    )

    private fun readSkin(): AppSkin =
        preferences.getString(KEY_SKIN, null)
            ?.let { value -> AppSkin.entries.firstOrNull { it.name == value } }
            ?: AppSkin.PAPER

    private fun readPersona(): ChatPersona =
        preferences.getString(KEY_PERSONA, null)
            ?.let { value -> ChatPersona.entries.firstOrNull { it.name == value } }
            ?: ChatPersona.WARM

    private fun encrypt(plainText: String): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return EncryptedValue(
            cipherText = Base64.encodeToString(
                cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            ),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    private fun decryptStored(cipherKey: String, ivKey: String): String? {
        val cipherText = preferences.getString(cipherKey, null) ?: return null
        val iv = preferences.getString(ivKey, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(
                cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private data class EncryptedValue(
        val cipherText: String,
        val iv: String,
    )

    private data class KeyPair(
        val cipherKey: String,
        val ivKey: String,
    )

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-1"

        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_ENCRYPTED_API_KEY = "encrypted_api_key"
        const val KEY_API_KEY_IV = "api_key_iv"
        const val KEY_TASKS = "include_tasks"
        const val KEY_SUGGESTIONS = "include_suggestions"
        const val KEY_REFLECTIONS = "include_reflections"
        const val KEY_PERSONA = "chat_persona"
        const val KEY_ALLOW_EDITING = "allow_editing"
        const val KEY_SKIN = "app_skin"

        const val KEY_VISION_USE_LLM = "vision_use_llm"
        const val KEY_VISION_BASE_URL = "vision_base_url"
        const val KEY_VISION_MODEL = "vision_model"
        const val KEY_ENCRYPTED_VISION_API_KEY = "encrypted_vision_api_key"
        const val KEY_VISION_API_KEY_IV = "vision_api_key_iv"

        const val KEY_TRANSCRIPTION_BASE_URL = "transcription_base_url"
        const val KEY_TRANSCRIPTION_MODEL = "transcription_model"
        const val KEY_ENCRYPTED_TRANSCRIPTION_API_KEY = "encrypted_transcription_api_key"
        const val KEY_TRANSCRIPTION_API_KEY_IV = "transcription_api_key_iv"
    }
}
