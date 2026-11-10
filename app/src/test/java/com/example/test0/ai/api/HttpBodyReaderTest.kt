package com.example.test0.ai.api

import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpBodyReaderTest {
    @Test
    fun oversizedBodyIsRejected() {
        val error = runCatching {
            readLimitedBody(
                stream = ByteArrayInputStream(ByteArray(10)),
                maxBytes = 5,
                deadlineNanos = Long.MAX_VALUE,
                nowNanos = { 0L },
                checkActive = {},
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("过大"))
    }

    @Test
    fun totalDeadlineAndCancellationAreEnforcedBetweenChunks() {
        var now = 0L
        val timeout = runCatching {
            readLimitedBody(
                stream = ByteArrayInputStream(ByteArray(10)),
                maxBytes = 20,
                deadlineNanos = 1L,
                nowNanos = { now++ },
                checkActive = {},
                bufferSize = 1,
            )
        }.exceptionOrNull()
        assertTrue(timeout?.message.orEmpty().contains("超时"))

        val cancellation = runCatching {
            readLimitedBody(
                stream = ByteArrayInputStream(ByteArray(1)),
                maxBytes = 2,
                deadlineNanos = Long.MAX_VALUE,
                nowNanos = { 0L },
                checkActive = { throw CancellationException("cancelled") },
            )
        }.exceptionOrNull()
        assertTrue(cancellation is CancellationException)
    }
}
