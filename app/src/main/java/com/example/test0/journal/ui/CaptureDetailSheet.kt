package com.example.test0.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.test0.R
import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import com.example.test0.journal.model.displayText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 点开随记后的整页阅览：整个界面就是这条笔记，长文可滚动看全，没有放大按钮。
 * 文字全文、图片预览或语音播放都在这页里。
 */
@Composable
fun CaptureDetailSheet(
    capture: Capture,
    onDismiss: () -> Unit,
    onEdit: ((Capture) -> Unit)? = null,
    onDelete: ((Capture) -> Unit)? = null,
    onRecognize: ((Capture) -> Unit)? = null,
    onTagsChanged: ((Capture, List<String>) -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val text = capture.displayText
    // capture 是打开详情时的快照；标签在本地乐观更新，增删后立刻可见，
    // 不必退出详情页再进来。
    var currentTags by remember(capture.id) { mutableStateOf(capture.tags) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("capture_close")) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.capture_close),
                        )
                    }
                    Text(
                        text = stringResource(R.string.capture_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (text.isNotBlank()) {
                        TextButton(
                            onClick = { clipboard.setText(AnnotatedString(text)) },
                            modifier = Modifier.testTag("capture_copy"),
                        ) {
                            Text(stringResource(R.string.capture_copy))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(
                            when (capture.type) {
                                CaptureType.TEXT -> R.string.capture_type_text
                                CaptureType.IMAGE -> R.string.capture_type_image
                                CaptureType.AUDIO -> R.string.capture_type_audio
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${formatFullDate(capture.dayKey)}  ${formatTime(capture.contentAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    when (capture.type) {
                        CaptureType.IMAGE -> ImagePreview(
                            path = capture.mediaPath,
                            modifier = Modifier.fillMaxWidth(),
                            height = 260.dp,
                        )

                        CaptureType.AUDIO -> AudioPlayer(path = capture.mediaPath)

                        CaptureType.TEXT -> Unit
                    }

                    if (text.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(18.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    if (onTagsChanged != null) {
                        TagSection(
                            tags = currentTags,
                            onChange = { newTags ->
                                currentTags = newTags
                                onTagsChanged(capture, newTags)
                            },
                        )
                    }

                    if (onRecognize != null && capture.type != CaptureType.TEXT) {
                        Button(
                            onClick = {
                                onRecognize(capture)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("capture_recognize"),
                        ) {
                            Text(
                                if (capture.type == CaptureType.IMAGE) {
                                    stringResource(R.string.capture_recognize_image)
                                } else {
                                    stringResource(R.string.capture_recognize_audio)
                                },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (onEdit != null && capture.type == CaptureType.TEXT) {
                            OutlinedButton(
                                onClick = { onEdit(capture) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.capture_edit_action))
                            }
                        }
                        if (onDelete != null) {
                            OutlinedButton(
                                onClick = { onDelete(capture) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.capture_delete_action))
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.capture_detail_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val fullDateFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private val timeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
        .withZone(ZoneId.systemDefault())

private fun formatFullDate(date: java.time.LocalDate): String = date.format(fullDateFormatter)

private fun formatTime(instant: java.time.Instant): String = timeFormatter.format(instant)
