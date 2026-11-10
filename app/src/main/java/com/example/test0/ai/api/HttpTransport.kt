package com.example.test0.ai.api

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class HttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val bodyBytes: ByteArray? = null,
)

data class HttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface HttpTransport {
    suspend fun post(request: HttpRequest): HttpResponse
}

data class MultipartField(val name: String, val value: String)

data class MultipartFile(
    val name: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

/** multipart/form-data 上传，用同一个 post 通道发出去，避免再开一套连接逻辑。 */
suspend fun HttpTransport.postMultipart(
    url: String,
    headers: Map<String, String>,
    fields: List<MultipartField>,
    file: MultipartFile,
): HttpResponse {
    val boundary = "----PersonalAINotes${System.nanoTime()}"
    val output = ByteArrayOutputStream()
    fun line(value: String = "") {
        output.write(value.toByteArray(Charsets.UTF_8))
        output.write("\r\n".toByteArray(Charsets.UTF_8))
    }
    fields.forEach { field ->
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"${field.name}\"")
        line()
        line(field.value)
    }
    line("--$boundary")
    line("Content-Disposition: form-data; name=\"${file.name}\"; filename=\"${file.fileName}\"")
    line("Content-Type: ${file.mimeType}")
    line()
    output.write(file.bytes)
    line()
    line("--$boundary--")

    return post(
        HttpRequest(
            url = url,
            headers = headers + ("Content-Type" to "multipart/form-data; boundary=$boundary"),
            body = "",
            bodyBytes = output.toByteArray(),
        ),
    )
}

class UrlConnectionHttpTransport : HttpTransport {
    override suspend fun post(request: HttpRequest): HttpResponse =
        withContext(Dispatchers.IO) {
            val coroutineContext = currentCoroutineContext()
            val deadline = System.nanoTime() + TOTAL_TIMEOUT_NANOS
            suspendCancellableCoroutine { continuation ->
                val connection = URL(request.url).openConnection() as HttpsURLConnection
                continuation.invokeOnCancellation { connection.disconnect() }
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = SOCKET_TIMEOUT_MILLIS
                    connection.readTimeout = SOCKET_TIMEOUT_MILLIS
                    connection.doOutput = true
                    request.headers.forEach(connection::setRequestProperty)
                    connection.outputStream.use { output ->
                        val bytes = request.bodyBytes
                        if (bytes != null) {
                            output.write(bytes)
                        } else {
                            output.write(request.body.toByteArray(Charsets.UTF_8))
                        }
                    }
                    val statusCode = connection.responseCode
                    val stream = if (statusCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                    val body = stream?.use {
                        readLimitedBody(
                            stream = it,
                            maxBytes = MAX_RESPONSE_BYTES,
                            deadlineNanos = deadline,
                            nowNanos = System::nanoTime,
                            checkActive = coroutineContext::ensureActive,
                        )
                    }.orEmpty()
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.success(HttpResponse(statusCode, body)),
                        )
                    }
                } catch (exception: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(mapTransportError(exception)))
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun mapTransportError(exception: Throwable): Throwable = when (exception) {
        is CancellationException -> exception
        is ResponseReadException -> IllegalStateException(exception.message, exception)
        else -> IllegalStateException("网络连接失败，请检查网络和 API 地址", exception)
    }

    private companion object {
        const val SOCKET_TIMEOUT_MILLIS = 15_000
        const val MAX_RESPONSE_BYTES = 1_000_000
        const val TOTAL_TIMEOUT_NANOS = 30_000_000_000L
    }
}

internal fun readLimitedBody(
    stream: InputStream,
    maxBytes: Int,
    deadlineNanos: Long,
    nowNanos: () -> Long,
    checkActive: () -> Unit,
    bufferSize: Int = 8_192,
): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, bufferSize))
    val buffer = ByteArray(bufferSize)
    while (true) {
        checkActive()
        if (nowNanos() >= deadlineNanos) {
            throw ResponseReadException("API 响应超时")
        }
        val count = stream.read(buffer)
        if (count < 0) break
        if (output.size() + count > maxBytes) {
            throw ResponseReadException("API 响应过大")
        }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

private class ResponseReadException(message: String) : Exception(message)
