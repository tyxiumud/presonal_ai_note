package com.example.test0.journal.ui

import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.test0.R
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 应用内录音：显示时长与音量波形，让用户明确知道正在录音、有没有声音。 */
@Composable
fun RecordingDialog(
    onDismiss: () -> Unit,
    onFinished: (File) -> Unit,
) {
    val context = LocalContext.current
    val target = remember {
        File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
    }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var amplitudes by remember { mutableStateOf(List(WAVE_BARS) { 0f }) }
    val failedText = stringResource(R.string.record_failed)
    val unavailableText = stringResource(R.string.record_unavailable)
    val primary = MaterialTheme.colorScheme.primary

    // 录音期间每 100ms 采样一次音量（MediaRecorder.maxAmplitude），并累计时长
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (isActive) {
            delay(100)
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            val level = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            val normalized = (level / MAX_AMPLITUDE).coerceIn(0f, 1f)
            amplitudes = (amplitudes.drop(1) + normalized)
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recorder?.release() } }
    }

    fun releaseRecorder() {
        runCatching { recorder?.release() }
        recorder = null
    }

    fun dismiss() {
        releaseRecorder()
        if (!recording) target.delete()
        onDismiss()
    }

    Dialog(onDismissRequest = ::dismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.record_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = if (recording) {
                        stringResource(R.string.record_running)
                    } else {
                        stringResource(R.string.record_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.record_duration,
                            elapsedSeconds / 60,
                            elapsedSeconds % 60,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (recording) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    val barWidth = size.width / (WAVE_BARS * 1.6f)
                    val gap = (size.width - barWidth * WAVE_BARS) / (WAVE_BARS - 1)
                    amplitudes.forEachIndexed { index, value ->
                        val height = (MIN_BAR + (1f - MIN_BAR) * value) * size.height
                        drawRoundRect(
                            color = primary.copy(alpha = 0.35f + 0.65f * value),
                            topLeft = Offset(
                                x = index * (barWidth + gap),
                                y = (size.height - height) / 2,
                            ),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                        )
                    }
                }

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        if (recording) {
                            try {
                                recorder?.stop()
                                recorder?.release()
                            } catch (throwable: Throwable) {
                                target.delete()
                                error = failedText
                                recorder = null
                                recording = false
                                return@Button
                            }
                            recorder = null
                            recording = false
                            onFinished(target)
                        } else {
                            try {
                                recorder = MediaRecorder().apply {
                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    setOutputFile(target.absolutePath)
                                    prepare()
                                    start()
                                }
                                recording = true
                                elapsedSeconds = 0
                                amplitudes = List(WAVE_BARS) { 0f }
                                error = null
                            } catch (_: Exception) {
                                releaseRecorder()
                                target.delete()
                                error = unavailableText
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (recording) {
                            stringResource(R.string.record_stop)
                        } else {
                            stringResource(R.string.record_start)
                        },
                    )
                }
                TextButton(onClick = ::dismiss) {
                    Text(stringResource(R.string.journal_date_cancel))
                }
            }
        }
    }
}

private const val WAVE_BARS = 28
private const val MIN_BAR = 0.08f
private const val MAX_AMPLITUDE = 20_000f
