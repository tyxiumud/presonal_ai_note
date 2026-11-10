package com.example.test0.ai.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.test0.ai.settings.AiConnectionSettings
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAiCompatibleApiClientTest {
    private val connection = AiConnectionSettings(
        baseUrl = "https://api.example.test/v1/",
        model = "demo-model",
        apiKey = "secret-key",
    )

    @Test
    fun analyzeBuildsCompatibleRequestAndParsesStructuredContent() = runTest {
        val transport = FakeTransport(
            HttpResponse(
                statusCode = 200,
                body = completion(
                    """{"summary":"今天很好","tasks":["散步"],"gentleSuggestions":["早点休息"],"reflectionQuestions":["什么最重要？"]}""",
                ),
            ),
        )
        val client = OpenAiCompatibleApiClient(transport)

        val result = client.analyze(
            connection = connection,
            request = DailyAnalysisRequest(
                day = LocalDate.parse("2026-11-05"),
                entries = listOf("上午完成了工作", "晚上散步"),
                options = DailyAnalysisOptions(),
            ),
        )

        assertEquals("今天很好", result.summary)
        assertEquals(listOf("散步"), result.tasks)
        assertEquals("https://api.example.test/v1/chat/completions", transport.request?.url)
        assertEquals("Bearer secret-key", transport.request?.headers?.get("Authorization"))
        assertTrue(transport.request?.body.orEmpty().contains("demo-model"))
        assertTrue(transport.request?.body.orEmpty().contains("上午完成了工作"))
    }

    @Test
    fun fencedJsonAndDisabledOptionsAreAccepted() = runTest {
        val transport = FakeTransport(
            HttpResponse(
                200,
                completion(
                    "\u0060\u0060\u0060json\n{\"summary\":\"摘要\",\"tasks\":[],\"gentleSuggestions\":[],\"reflectionQuestions\":[]}\n\u0060\u0060\u0060",
                ),
            ),
        )
        val client = OpenAiCompatibleApiClient(transport)

        val result = client.analyze(
            connection,
            DailyAnalysisRequest(
                day = LocalDate.parse("2026-11-05"),
                entries = listOf("记录"),
                options = DailyAnalysisOptions(
                    includeTasks = false,
                    includeSuggestions = false,
                    includeReflections = false,
                ),
            ),
        )

        assertEquals("摘要", result.summary)
        assertTrue(transport.request?.body.orEmpty().contains("不要生成任务"))
    }

    @Test
    fun nonSuccessResponseDoesNotExposeResponseBody() = runTest {
        val client = OpenAiCompatibleApiClient(
            FakeTransport(HttpResponse(401, "provider-private-error-body")),
        )

        val error = runCatching {
            client.testConnection(connection)
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("401"))
        assertTrue(!error?.message.orEmpty().contains("provider-private"))
    }

    @Test
    fun missingChoicesAndInvalidStructuredContentFailClearly() = runTest {
        val missing = OpenAiCompatibleApiClient(FakeTransport(HttpResponse(200, "{}")))
        val invalid = OpenAiCompatibleApiClient(
            FakeTransport(HttpResponse(200, completion("not-json"))),
        )

        assertTrue(
            runCatching { missing.testConnection(connection) }
                .exceptionOrNull()?.message.orEmpty().contains("有效内容"),
        )
        assertTrue(
            runCatching {
                invalid.analyze(
                    connection,
                    DailyAnalysisRequest(
                        LocalDate.parse("2026-11-05"),
                        listOf("记录"),
                        DailyAnalysisOptions(),
                    ),
                )
            }.exceptionOrNull()?.message.orEmpty().contains("格式"),
        )
    }

    @Test
    fun missingWrongTypeOrNonStringStructuredFieldsAreRejected() = runTest {
        val malformed = listOf(
            """{"summary":"摘要","gentleSuggestions":[],"reflectionQuestions":[]}""",
            """{"summary":"摘要","tasks":"任务","gentleSuggestions":[],"reflectionQuestions":[]}""",
            """{"summary":"摘要","tasks":[123],"gentleSuggestions":[],"reflectionQuestions":[]}""",
        )

        malformed.forEach { content ->
            val client = OpenAiCompatibleApiClient(
                FakeTransport(HttpResponse(200, completion(content))),
            )
            val error = runCatching {
                client.analyze(
                    connection,
                    DailyAnalysisRequest(
                        LocalDate.parse("2026-11-05"),
                        listOf("记录"),
                        DailyAnalysisOptions(),
                    ),
                )
            }.exceptionOrNull()

            assertTrue(error?.message.orEmpty().contains("格式"))
        }
    }

    @Test
    fun describeImageSendsDataUrlAndUsesVisionModel() = runTest {
        val transport = FakeTransport(HttpResponse(200, completion("桌上有一杯茶和一本书。")))
        val client = OpenAiCompatibleApiClient(transport)

        val result = client.describeImage(
            connection = connection,
            request = ImageDescriptionRequest(
                imageBytes = byteArrayOf(1, 2, 3),
                mimeType = "image/jpeg",
                model = "vision-model",
                prompt = "描述图片",
            ),
        )

        assertEquals("桌上有一杯茶和一本书。", result)
        val body = transport.request?.body.orEmpty()
        assertTrue(body.contains("\"type\":\"image_url\""))
        assertTrue(body.contains("base64"))
        assertTrue(body.contains("image"))
        assertTrue(body.contains("vision-model"))
        assertEquals("https://api.example.test/v1/chat/completions", transport.request?.url)
    }

    @Test
    fun transcribeUsesAudioEndpointAndMultipart() = runTest {
        val transport = FakeTransport(HttpResponse(200, """{"text":"今天天气不错"}"""))
        val client = OpenAiCompatibleApiClient(transport)

        val result = client.transcribe(
            connection = connection,
            request = TranscriptionRequest(
                audioBytes = byteArrayOf(9, 8, 7),
                fileName = "voice.m4a",
                mimeType = "audio/mp4",
                model = "whisper-1",
            ),
        )

        assertEquals("今天天气不错", result)
        assertEquals("https://api.example.test/v1/audio/transcriptions", transport.request?.url)
        assertTrue(transport.request?.headers?.get("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val multipart = String(transport.request?.bodyBytes ?: ByteArray(0), Charsets.UTF_8)
        assertTrue(multipart.contains("name=\"model\""))
        assertTrue(multipart.contains("whisper-1"))
        assertTrue(multipart.contains("filename=\"voice.m4a\""))
    }

    @Test
    fun insecureEndpointIsRejectedBeforeTransport() = runTest {
        val transport = FakeTransport(HttpResponse(200, completion("OK")))
        val client = OpenAiCompatibleApiClient(transport)

        val error = runCatching {
            client.testConnection(connection.copy(baseUrl = "http://api.example.test/v1"))
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("HTTPS"))
        assertEquals(null, transport.request)
    }

    private fun completion(content: String): String = JSONObject()
        .put(
            "choices",
            JSONArray().put(
                JSONObject().put(
                    "message",
                    JSONObject().put("content", content),
                ),
            ),
        )
        .toString()
}

private class FakeTransport(
    private val response: HttpResponse,
) : HttpTransport {
    var request: HttpRequest? = null

    override suspend fun post(request: HttpRequest): HttpResponse {
        this.request = request
        return response
    }
}
