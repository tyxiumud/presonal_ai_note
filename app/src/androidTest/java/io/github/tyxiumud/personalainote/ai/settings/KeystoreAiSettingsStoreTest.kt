package io.github.tyxiumud.personalainote.ai.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreAiSettingsStoreTest {
    @Test
    fun keyRoundTripsEncryptedAndCanBeCleared() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val preferencesName = "ai-settings-test-$suffix"
        val store = KeystoreAiSettingsStore(
            context = context,
            preferencesName = preferencesName,
            keyAlias = "ai-settings-test-key-$suffix",
        )
        val secret = "sk-plain-secret-must-not-appear"

        store.save(
            AiSettingsInput(
                baseUrl = "https://api.example/v1",
                model = "demo-model",
                newApiKey = secret,
            ),
        )

        assertEquals(secret, store.connection()?.apiKey)
        val storedText = context.getSharedPreferences(preferencesName, 0)
            .all
            .values
            .joinToString()
        assertFalse(storedText.contains(secret))

        store.clearApiKey()
        assertNull(store.connection())
    }

    @Test
    fun separateMediaConnectionsResolveIndependentlyAndKeysAreEncrypted() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val preferencesName = "ai-settings-media-test-$suffix"
        val store = KeystoreAiSettingsStore(
            context = context,
            preferencesName = preferencesName,
            keyAlias = "ai-settings-media-test-key-$suffix",
        )

        store.save(
            AiSettingsInput(
                baseUrl = "https://llm.example/v1",
                model = "chat-model",
                newApiKey = "llm-secret",
                visionUseLlm = false,
                visionBaseUrl = "https://vision.example/v1",
                visionModel = "vision-model",
                newVisionApiKey = "vision-secret",
                transcriptionBaseUrl = "https://audio.example/v1",
                transcriptionModel = "my-whisper",
                newTranscriptionApiKey = "audio-secret",
            ),
        )

        val llm = store.connection()
        assertEquals("llm-secret", llm?.apiKey)
        val vision = store.visionConnection()
        assertEquals("https://vision.example/v1", vision?.baseUrl)
        assertEquals("vision-model", vision?.model)
        assertEquals("vision-secret", vision?.apiKey)
        val audio = store.transcriptionConnection()
        assertEquals("https://audio.example/v1", audio?.baseUrl)
        assertEquals("my-whisper", audio?.model)
        assertEquals("audio-secret", audio?.apiKey)

        val snapshot = store.settings.value
        assertFalse(snapshot.visionUseLlm)
        assertTrue(snapshot.hasVisionApiKey)
        assertTrue(snapshot.hasTranscriptionApiKey)

        val storedText = context.getSharedPreferences(preferencesName, 0)
            .all.values.joinToString()
        assertFalse(storedText.contains("vision-secret"))
        assertFalse(storedText.contains("audio-secret"))

        store.clearVisionApiKey()
        assertNull(store.visionConnection())
        context.deleteSharedPreferences(preferencesName)
    }

    @Test
    fun visionUseLlmReusesChatConnectionWithOptionalModelOverride() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val preferencesName = "ai-settings-reuse-test-$suffix"
        val store = KeystoreAiSettingsStore(
            context = context,
            preferencesName = preferencesName,
            keyAlias = "ai-settings-reuse-test-key-$suffix",
        )

        store.save(
            AiSettingsInput(
                baseUrl = "https://llm.example/v1",
                model = "chat-model",
                newApiKey = "llm-secret",
                visionUseLlm = true,
                visionModel = "vision-model",
            ),
        )

        val vision = store.visionConnection()
        assertEquals("https://llm.example/v1", vision?.baseUrl)
        assertEquals("vision-model", vision?.model)
        assertEquals("llm-secret", vision?.apiKey)
        assertEquals("whisper-1", store.transcriptionConnection()?.model)
        context.deleteSharedPreferences(preferencesName)
    }

    @Test
    fun editingSwitchAndSkinPersistIndependently() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val preferencesName = "ai-settings-prefs-$suffix"
        val store = KeystoreAiSettingsStore(
            context = context,
            preferencesName = preferencesName,
            keyAlias = "ai-settings-prefs-key-$suffix",
        )

        assertFalse(store.settings.value.allowEditing)
        assertEquals(AppSkin.PAPER, store.settings.value.skin)

        store.setAllowEditing(true)
        store.setSkin(AppSkin.NIGHT)

        assertTrue(store.settings.value.allowEditing)
        assertEquals(AppSkin.NIGHT, store.settings.value.skin)

        // 重新打开 store 模拟重启，偏好仍在
        val reopened = KeystoreAiSettingsStore(
            context = context,
            preferencesName = preferencesName,
            keyAlias = "ai-settings-prefs-key-$suffix",
        )
        assertTrue(reopened.settings.value.allowEditing)
        assertEquals(AppSkin.NIGHT, reopened.settings.value.skin)
        context.deleteSharedPreferences(preferencesName)
    }
}
