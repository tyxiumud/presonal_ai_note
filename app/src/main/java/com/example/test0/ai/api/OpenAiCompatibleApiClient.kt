package com.example.test0.ai.api

import android.util.Base64
import com.example.test0.ai.settings.AiConnectionSettings
import java.net.URI
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class OpenAiCompatibleApiClient(
    private val transport: HttpTransport = UrlConnectionHttpTransport(),
) : DailyAnalysisApi, ChatApi, MediaUnderstandingApi {
    override suspend fun testConnection(connection: AiConnectionSettings) {
        val content = requestContent(
            connection = connection,
            systemInstruction = "你正在进行连接测试。请只回复 OK。",
            userContent = "测试连接",
        )
        if (content.isBlank()) {
            throw AiApiException("API 没有返回有效内容")
        }
    }

    override suspend fun analyze(
        connection: AiConnectionSettings,
        request: DailyAnalysisRequest,
    ): StructuredDailyAnalysis {
        val options = request.options
        val optionInstructions = buildString {
            appendLine("summary 必须是字符串。")
            appendLine(
                if (options.includeTasks) {
                    "tasks 必须是任务字符串数组。"
                } else {
                    "不要生成任务，tasks 必须是空数组。"
                },
            )
            appendLine(
                if (options.includeSuggestions) {
                    "gentleSuggestions 必须是克制、温和的建议字符串数组。"
                } else {
                    "不要生成建议，gentleSuggestions 必须是空数组。"
                },
            )
            appendLine(
                if (options.includeReflections) {
                    "reflectionQuestions 必须是值得继续思考的问题字符串数组。"
                } else {
                    "不要生成思考问题，reflectionQuestions 必须是空数组。"
                },
            )
        }
        val entries = request.entries
            .mapIndexed { index, text -> "${index + 1}. ${text.trim()}" }
            .joinToString(separator = "\n")
        val content = requestContent(
            connection = connection,
            systemInstruction = """
                你负责整理用户主动提交的私人随记。
                只能依据所给记录，不得编造事实、诊断用户或把建议写成命令。
                只返回一个 JSON 对象，不要解释，不要 Markdown 代码围栏。
                JSON 必须包含 summary、tasks、gentleSuggestions、reflectionQuestions 四个键。
                $optionInstructions
            """.trimIndent(),
            userContent = """
                内容日期：${request.day}
                原始记录：
                $entries
            """.trimIndent(),
        )
        return parseStructuredContent(content)
    }

    override suspend fun reply(
        connection: AiConnectionSettings,
        request: ChatRequest,
    ): String {
        val hasContext = request.context.isNotBlank()
        val contextRule = if (hasContext) {
            "回答只能依据下面的本地资料，可以顺带提一下是哪天记的。资料里没写的就别替用户补。\n\n本地资料：\n${request.context}"
        } else {
            "这次没找到相关的本地资料。请先自然地说明你没看到相关记录，再给一点一般性的想法，不要假装记得用户的事。"
        }
        val content = requestContent(
            connection = connection,
            systemInstruction = """
                你是「小札」，一个陪用户回看自己随记的私人助手。你读过用户愿意分享的片段，
                说话像一位细心、不多话的朋友，而不是客服或报告生成器。

                这一轮请按这样的性格说话：
                ${request.persona.instruction}

                共同的底线：
                - 用简体中文，句子自然，不要用"首先/其次/总之"这类报告腔，也不要每轮都问候。
                - 只依据下面的本地资料回答；资料里没有的就坦白说没有，不猜测、不编造。
                - 不做心理诊断，不评判用户，不主动给人生建议，不写鸡汤式结尾。
                - 回答控制在 3 段以内，不输出 Markdown 代码块。
                $contextRule
            """.trimIndent(),
            userContent = request.question,
            history = request.history,
        )
        return content.trim()
    }

    override suspend fun describeImage(
        connection: AiConnectionSettings,
        request: ImageDescriptionRequest,
    ): String {
        val encoded = Base64.encodeToString(request.imageBytes, Base64.NO_WRAP)
        val content = JSONArray()
            .put(
                JSONObject()
                    .put("type", "text")
                    .put("text", request.prompt),
            )
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:${request.mimeType};base64,$encoded"),
                    ),
            )
        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", content),
        )
        val body = JSONObject()
            .put("model", request.model)
            .put("messages", messages)
            .put("max_tokens", 300)
            .toString()
        val response = transport.post(
            HttpRequest(
                url = endpoint(connection.baseUrl),
                headers = authHeaders(connection),
                body = body,
            ),
        )
        return extractContent(response)
    }

    override suspend fun transcribe(
        connection: AiConnectionSettings,
        request: TranscriptionRequest,
    ): String {
        val response = transport.postMultipart(
            url = audioEndpoint(connection.baseUrl),
            headers = mapOf("Authorization" to "Bearer ${connection.apiKey}"),
            fields = listOf(MultipartField("model", request.model)),
            file = MultipartFile(
                name = "file",
                fileName = request.fileName,
                mimeType = request.mimeType,
                bytes = request.audioBytes,
            ),
        )
        if (response.statusCode !in 200..299) {
            throw AiApiException("转写接口返回 HTTP ${response.statusCode}")
        }
        return try {
            JSONObject(response.body)
                .optString("text")
                .trim()
                .takeIf(String::isNotEmpty)
                ?: throw AiApiException("转写接口没有返回文字")
        } catch (exception: JSONException) {
            throw AiApiException("转写接口返回格式不正确", exception)
        }
    }

    private fun authHeaders(connection: AiConnectionSettings): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${connection.apiKey}",
        "Content-Type" to "application/json; charset=utf-8",
    )

    private suspend fun requestContent(
        connection: AiConnectionSettings,
        systemInstruction: String,
        userContent: String,
        history: List<ChatTurn> = emptyList(),
    ): String {
        val endpoint = endpoint(connection.baseUrl)
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemInstruction),
            )
        history.forEach { turn ->
            messages.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.content),
            )
        }
        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", userContent),
        )
        val body = JSONObject()
            .put("model", connection.model)
            .put("messages", messages)
            .toString()
        val response = transport.post(
            HttpRequest(
                url = endpoint,
                headers = authHeaders(connection),
                body = body,
            ),
        )
        return extractContent(response)
    }

    private fun extractContent(response: HttpResponse): String {
        if (response.statusCode !in 200..299) {
            throw AiApiException("API 返回 HTTP ${response.statusCode}")
        }
        return try {
            JSONObject(response.body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .takeIf(String::isNotBlank)
                ?: throw AiApiException("API 没有返回有效内容")
        } catch (exception: JSONException) {
            throw AiApiException("API 没有返回有效内容", exception)
        }
    }

    private fun parseStructuredContent(content: String): StructuredDailyAnalysis {
        val cleaned = content.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val json = JSONObject(cleaned)
            val summary = (json.opt("summary") as? String)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: throw AiApiException("AI 整理结果格式不正确")
            StructuredDailyAnalysis(
                summary = summary,
                tasks = json.requiredStringList("tasks"),
                gentleSuggestions = json.requiredStringList("gentleSuggestions"),
                reflectionQuestions = json.requiredStringList("reflectionQuestions"),
            )
        } catch (exception: JSONException) {
            throw AiApiException("AI 整理结果格式不正确", exception)
        }
    }

    private fun endpoint(baseUrl: String): String =
        "${validatedBase(baseUrl)}/chat/completions"

    private fun audioEndpoint(baseUrl: String): String =
        "${validatedBase(baseUrl)}/audio/transcriptions"

    private fun validatedBase(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
            .removeSuffix("/chat/completions")
            .removeSuffix("/audio/transcriptions")
            .trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
            throw AiApiException("API 地址必须是有效的 HTTPS 地址")
        }
        return trimmed
    }
}

class AiApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

private fun JSONObject.requiredStringList(key: String): List<String> {
    val array = opt(key) as? JSONArray
        ?: throw AiApiException("AI 整理结果格式不正确")
    return buildList {
        for (index in 0 until array.length()) {
            val value = (array.opt(index) as? String)
                ?.trim()
                ?: throw AiApiException("AI 整理结果格式不正确")
            if (value.isNotEmpty()) add(value)
        }
    }
}
