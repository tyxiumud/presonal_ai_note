package com.example.test0.journal.ui

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.test0.R
import androidx.compose.foundation.Image as ComposeImage

/** 从私有文件解码缩略图；文件丢失或不是图片时显示一个占位。 */
@Composable
fun ImagePreview(
    path: String?,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val bitmap = remember(path) {
        if (path == null) {
            null
        } else {
            runCatching {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        ComposeImage(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .height(height)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .height(height)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_image),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 语音播放：一个按钮 + 状态文字，离开组合时释放 MediaPlayer。 */
@Composable
fun AudioPlayer(
    path: String?,
    modifier: Modifier = Modifier,
) {
    val player = remember(path) {
        if (path == null) {
            null
        } else {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                }
            }.getOrNull()
        }
    }
    var playing by remember(path) { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose {
            player?.release()
            playing = false
        }
    }
    LaunchedEffect(player) {
        player?.setOnCompletionListener { playing = false }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = {
                if (player == null) return@Surface
                if (playing) {
                    player.pause()
                    playing = false
                } else {
                    runCatching { player.start() }
                    playing = true
                }
            },
            enabled = player != null,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = when {
                player == null -> stringResource(R.string.capture_audio_unavailable)
                playing -> stringResource(R.string.capture_audio_playing)
                else -> stringResource(R.string.capture_audio_ready)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
