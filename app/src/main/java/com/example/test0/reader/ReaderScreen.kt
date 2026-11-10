package com.example.test0.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.test0.R
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import com.example.test0.journal.model.displayText
import com.example.test0.journal.ui.AudioPlayer
import com.example.test0.journal.ui.CaptureDetailSheet
import com.example.test0.journal.ui.ImagePreview
import com.example.test0.ui.components.SectionTitle
import com.example.test0.ui.components.VaultCard
import com.example.test0.ui.components.VaultEmptyState
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReaderRoute(viewModel: ReaderViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(
        state = state,
        onDateSelected = viewModel::onDateSelected,
        onRetry = viewModel::onRetry,
        onDeleteAnalysis = viewModel::deleteAnalysis,
        onRecognizeCapture = viewModel::recognizeMedia,
        onTagsChanged = viewModel::setTags,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onDateSelected: (LocalDate) -> Unit,
    onRetry: () -> Unit = {},
    onDeleteAnalysis: (LocalDate) -> Unit = {},
    onRecognizeCapture: (Capture) -> Unit = {},
    onTagsChanged: (Capture, List<String>) -> Unit = { _, _ -> },
) {
    var pendingDetail by remember { mutableStateOf<DailyAnalysis?>(null) }
    var pendingDelete by remember { mutableStateOf<LocalDate?>(null) }
    var pendingCapture by remember { mutableStateOf<Capture?>(null) }

    pendingCapture?.let { capture ->
        CaptureDetailSheet(
            capture = capture,
            onDismiss = { pendingCapture = null },
            onRecognize = {
                pendingCapture = null
                onRecognizeCapture(capture)
            },
            onTagsChanged = { item, tags -> onTagsChanged(item, tags) },
        )
    }

    pendingDetail?.let { analysis ->
        AnalysisDetailSheet(
            analysis = analysis,
            onDismiss = { pendingDetail = null },
        )
    }

    pendingDelete?.let { day ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.reader_delete_analysis_title)) },
            text = { Text(stringResource(R.string.reader_delete_analysis_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAnalysis(day)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag("reader_delete_confirm"),
                ) {
                    Text(stringResource(R.string.reader_delete_analysis_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.journal_date_cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.days.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.days, key = LocalDate::toString) { day ->
                        DayPill(
                            day = day,
                            selected = day == state.selectedDate,
                            onClick = { onDateSelected(day) },
                        )
                    }
                }
            }

            when {
                state.isLoading -> LoadingContent()
                state.errorMessage != null -> ErrorContent(
                    message = state.errorMessage,
                    canRetry = state.canRetry,
                    onRetry = onRetry,
                )
                state.selectedDate == null -> CenterMessage(stringResource(R.string.reader_empty))
                else -> DailyContent(
                    state = state,
                    onOpenAnalysis = { pendingDetail = it },
                    onDeleteAnalysis = { pendingDelete = it },
                    onOpenCapture = { pendingCapture = it },
                )
            }
        }
    }
}

@Composable
private fun DayPill(
    day: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = formatDate(day),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DailyContent(
    state: ReaderUiState,
    onOpenAnalysis: (DailyAnalysis) -> Unit,
    onDeleteAnalysis: (LocalDate) -> Unit,
    onOpenCapture: (Capture) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.actionMessage != null) {
            item {
                Text(
                    text = state.actionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            SectionTitle(
                text = stringResource(R.string.reader_analysis_section),
                trailing = stringResource(R.string.reader_analysis_count, state.analyses.size),
            )
        }

        if (state.analyses.isEmpty()) {
            item {
                VaultEmptyState(
                    text = stringResource(R.string.reader_analysis_empty),
                    iconRes = R.drawable.ic_sparkle,
                )
            }
        } else {
            items(state.analyses, key = DailyAnalysis::day) { analysis ->
                AnalysisEntryCard(
                    analysis = analysis,
                    isStale = analysis.day == state.selectedDate && state.isAnalysisStale,
                    onOpen = { onOpenAnalysis(analysis) },
                    onDelete = { onDeleteAnalysis(analysis.day) },
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle(
                text = stringResource(R.string.reader_raw_title),
                trailing = "${state.captures.size} 条",
            )
        }

        if (state.captures.isEmpty()) {
            item {
                VaultEmptyState(
                    text = stringResource(R.string.reader_no_captures),
                    iconRes = R.drawable.ic_nav_journal,
                )
            }
        } else {
            items(state.captures, key = Capture::id) { capture ->
                VaultCard(
                    onClick = { onOpenCapture(capture) },
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = formatTime(capture),
                            modifier = Modifier.width(44.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (capture.type) {
                                    CaptureType.TEXT -> capture.displayText.ifBlank {
                                        stringResource(R.string.capture_type_text)
                                    }
                                    CaptureType.IMAGE -> stringResource(R.string.capture_type_image)
                                    CaptureType.AUDIO -> stringResource(R.string.capture_type_audio)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            when (capture.type) {
                                CaptureType.IMAGE -> {
                                    Spacer(Modifier.height(8.dp))
                                    ImagePreview(
                                        path = capture.mediaPath,
                                        modifier = Modifier.fillMaxWidth(),
                                        height = 90.dp,
                                    )
                                }

                                CaptureType.AUDIO -> {
                                    Spacer(Modifier.height(8.dp))
                                    AudioPlayer(path = capture.mediaPath)
                                }

                                CaptureType.TEXT -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisEntryCard(
    analysis: DailyAnalysis,
    isStale: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    VaultCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatFullDate(analysis.day),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = analysisPreview(analysis.markdown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                if (isStale) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.reader_analysis_stale_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .testTag("reader_delete_analysis"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.reader_delete_analysis),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisDetailSheet(
    analysis: DailyAnalysis,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.reader_analysis_detail),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = formatFullDate(analysis.day),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reader_analysis_readonly),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(20.dp)) {
                    MarkdownBody(markdown = analysis.markdown)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.reader_model, analysis.modelName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarkdownBody(markdown: String) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = block.text,
                    style = if (block.level <= 1) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = if (block.level <= 1) 0.dp else 6.dp),
                )

                is MarkdownBlock.Quote -> Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                    )
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is MarkdownBlock.Bullet -> Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = when (block.checked) {
                            true -> "☑"
                            false -> "☐"
                            null -> "·"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = block.text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                is MarkdownBlock.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data class Bullet(val text: String, val checked: Boolean?) : MarkdownBlock

    data class Paragraph(val text: String) : MarkdownBlock
}

/** 只覆盖每日整理实际会产生的语法：标题、引用、列表和段落。 */
internal fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks += MarkdownBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> flushParagraph()
            line.startsWith("### ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(3, line.removePrefix("### ").trim())
            }
            line.startsWith("## ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(2, line.removePrefix("## ").trim())
            }
            line.startsWith("# ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(1, line.removePrefix("# ").trim())
            }
            line.startsWith("> ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(line.removePrefix("> ").trim())
            }
            line.startsWith("- [ ] ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(line.removePrefix("- [ ] ").trim(), checked = false)
            }
            line.startsWith("- [x] ", ignoreCase = true) -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(line.substring(6).trim(), checked = true)
            }
            line.startsWith("- ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(line.removePrefix("- ").trim(), checked = null)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    flushParagraph()
    return blocks
}

private fun analysisPreview(markdown: String): String {
    val lines = markdown.lines()
    val headingIndex = lines.indexOfFirst { it.trim() == "## 当日摘要" }
    val body = if (headingIndex >= 0) {
        lines.drop(headingIndex + 1).takeWhile { !it.trimStart().startsWith("## ") }
    } else {
        lines
    }
    return body.map(String::trim)
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(">") }
        ?.take(48)
        ?: "查看这一天的整理"
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun ErrorContent(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        if (canRetry) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.journal_retry))
            }
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    VaultEmptyState(
        text = message,
        iconRes = R.drawable.ic_nav_reader,
    )
}

private val readerDateFormatter =
    DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

private val readerFullDateFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private val readerTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

private fun formatDate(day: LocalDate): String = day.format(readerDateFormatter)

private fun formatFullDate(day: LocalDate): String = day.format(readerFullDateFormatter)

private fun formatTime(capture: Capture): String =
    capture.contentAt.atZone(ZoneId.systemDefault()).format(readerTimeFormatter)
