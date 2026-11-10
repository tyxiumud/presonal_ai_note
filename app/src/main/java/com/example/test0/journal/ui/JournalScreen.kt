package com.example.test0.journal.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.test0.R
import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import com.example.test0.journal.model.displayText
import com.example.test0.ui.components.SectionTitle
import com.example.test0.ui.components.VaultCard
import com.example.test0.ui.components.VaultEmptyState
import com.example.test0.ui.theme.AccentAmber
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun JournalRoute(
    viewModel: JournalViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importMedia(it, CaptureType.IMAGE) } }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importMedia(it, CaptureType.AUDIO) } }

    var showRecorder by remember { mutableStateOf(false) }

    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showRecorder = true
        } else {
            audioPicker.launch(arrayOf("audio/*"))
        }
    }

    if (showRecorder) {
        RecordingDialog(
            onDismiss = { showRecorder = false },
            onFinished = { file ->
                showRecorder = false
                viewModel.importMedia(Uri.fromFile(file), CaptureType.AUDIO)
            },
        )
    }

    JournalScreen(
        state = state,
        today = LocalDate.now(),
        onDraftChanged = viewModel::onDraftChanged,
        onDateSelected = viewModel::onDateSelected,
        onSave = viewModel::save,
        onRetry = viewModel::onRetry,
        onAnalyze = viewModel::analyzeSelectedDay,
        onDeleteCapture = viewModel::deleteCapture,
        onEditCapture = viewModel::editCapture,
        onRecognizeCapture = viewModel::recognizeMedia,
        onTagsChanged = viewModel::setTags,
        onPickImage = { imagePicker.launch(arrayOf("image/*")) },
        onPickAudio = { recordPermission.launch(Manifest.permission.RECORD_AUDIO) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    state: JournalUiState,
    today: LocalDate,
    onDraftChanged: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onAnalyze: () -> Unit = {},
    onDeleteCapture: (Capture) -> Unit = {},
    onEditCapture: (Capture, String) -> Unit = { _, _ -> },
    onPickImage: () -> Unit = {},
    onPickAudio: () -> Unit = {},
    onRecognizeCapture: (Capture) -> Unit = {},
    onTagsChanged: (Capture, List<String>) -> Unit = { _, _ -> },
) {
    var showDatePicker by remember { mutableStateOf(value = false) }
    var pendingDelete by remember { mutableStateOf<Capture?>(null) }
    var pendingEdit by remember { mutableStateOf<Capture?>(null) }
    var pendingDetail by remember { mutableStateOf<Capture?>(null) }
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    var focusInput by remember { mutableStateOf(false) }

    LaunchedEffect(focusInput) {
        if (focusInput) {
            listState.animateScrollToItem(0)
            inputFocus.requestFocus()
            focusInput = false
        }
    }

    pendingDetail?.let { target ->
        CaptureDetailSheet(
            capture = target,
            onDismiss = { pendingDetail = null },
            onEdit = if (state.canEdit) {
                {
                    pendingDetail = null
                    pendingEdit = it
                }
            } else {
                null
            },
            onDelete = {
                pendingDetail = null
                pendingDelete = it
            },
            onRecognize = onRecognizeCapture,
            onTagsChanged = { capture, tags -> onTagsChanged(capture, tags) },
        )
    }

    pendingDelete?.let { target ->
        DeleteCaptureDialog(
            onConfirm = {
                onDeleteCapture(target)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingEdit?.let { target ->
        EditCaptureDialog(
            target = target,
            onConfirm = { text ->
                onEditCapture(target, text)
                pendingEdit = null
            },
            onDismiss = { pendingEdit = null },
        )
    }

    if (showDatePicker) {
        JournalCalendarDialog(
            selectedDate = state.selectedDate,
            markedDays = state.markedDays,
            today = today,
            onDateSelected = {
                onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { focusInput = true },
                containerColor = AccentAmber,
                contentColor = Color(0xFF2A2419),
                shape = CircleShape,
                modifier = Modifier.testTag("journal_add"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.journal_add),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                JournalHeader(
                    selectedDate = state.selectedDate,
                    onOpenPicker = { showDatePicker = true },
                )
            }

            item {
                DateChips(
                    today = today,
                    selectedDate = state.selectedDate,
                    markedDays = state.markedDays,
                    onDateSelected = onDateSelected,
                    onOpenPicker = { showDatePicker = true },
                )
            }

            item {
                ComposerCard(
                    state = state,
                    focusRequester = inputFocus,
                    onDraftChanged = onDraftChanged,
                    onSave = onSave,
                    onAnalyze = onAnalyze,
                    onPickImage = onPickImage,
                    onPickAudio = onPickAudio,
                )
            }

            item {
                SectionTitle(
                    text = stringResource(R.string.journal_timeline_title),
                    trailing = "${state.captures.size} 条",
                )
            }

            when {
                state.readErrorMessage != null -> item {
                    MessageCard(
                        message = state.readErrorMessage,
                        isError = true,
                        actionLabel = stringResource(R.string.journal_retry),
                        onAction = onRetry,
                    )
                }

                state.isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }

                state.captures.isEmpty() -> item {
                    VaultEmptyState(
                        text = stringResource(R.string.journal_empty),
                        iconRes = R.drawable.ic_nav_journal,
                    )
                }

                else -> items(state.captures.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { capture ->
                            CaptureNoteCard(
                                capture = capture,
                                canEdit = state.canEdit,
                                onOpen = { pendingDetail = capture },
                                onEdit = { pendingEdit = capture },
                                onDelete = { pendingDelete = capture },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(176.dp),
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 大标题 + 当前日期，参考笔记类 App 的留白。 */
@Composable
private fun JournalHeader(
    selectedDate: LocalDate,
    onOpenPicker: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.journal_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.journal_home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("journal_date"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_calendar),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = formatDate(selectedDate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 有记录的日期做成一排 chip，点一下就跳到那天。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChips(
    today: LocalDate,
    selectedDate: LocalDate,
    markedDays: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val days = buildList {
        add(today)
        markedDays
            .filter { it != today }
            .sortedDescending()
            .take(4)
            .forEach { add(it) }
    }.distinct()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEach { day ->
            SuggestionChip(
                onClick = { onDateSelected(day) },
                label = {
                    Text(
                        if (day == today) {
                            stringResource(R.string.journal_home_today)
                        } else {
                            formatShortDate(day)
                        },
                    )
                },
                shape = CircleShape,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (day == selectedDate) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    labelColor = if (day == selectedDate) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
                border = null,
            )
        }
        SuggestionChip(
            onClick = onOpenPicker,
            label = { Text(stringResource(R.string.journal_date_pick)) },
            shape = CircleShape,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            border = null,
        )
    }
}

@Composable
private fun ComposerCard(
    state: JournalUiState,
    focusRequester: FocusRequester,
    onDraftChanged: (String) -> Unit,
    onSave: () -> Unit,
    onAnalyze: () -> Unit,
    onPickImage: () -> Unit,
    onPickAudio: () -> Unit,
) {
    VaultCard {
        Text(
            text = stringResource(R.string.journal_input_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.draft,
            onValueChange = onDraftChanged,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("journal_input"),
            placeholder = { Text(stringResource(R.string.journal_input_hint)) },
            minLines = 3,
            enabled = !state.isSaving,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.journal_input_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(
                onClick = onPickImage,
                label = { Text(stringResource(R.string.journal_add_image)) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_image),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                shape = CircleShape,
                border = null,
                modifier = Modifier.testTag("journal_add_image"),
            )
            SuggestionChip(
                onClick = onPickAudio,
                label = { Text(stringResource(R.string.journal_add_audio)) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                shape = CircleShape,
                border = null,
                modifier = Modifier.testTag("journal_add_audio"),
            )
        }
        if (state.validationMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.validationMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSave,
            enabled = state.draft.isNotBlank() && !state.isSaving,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("journal_save"),
        ) {
            Text(
                if (state.isSaving) {
                    stringResource(R.string.journal_saving)
                } else {
                    stringResource(R.string.journal_save)
                },
            )
        }
        TextButton(
            onClick = onAnalyze,
            enabled = state.captures.isNotEmpty() && !state.isAnalyzing,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("journal_analyze"),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sparkle),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.isAnalyzing) {
                    stringResource(R.string.journal_analyzing)
                } else {
                    stringResource(R.string.journal_analyze)
                },
            )
        }
        if (state.analysisMessage != null) {
            Text(
                text = state.analysisMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 双列等高卡片：文字 / 图片 / 语音共用一张卡，点卡片可以打开详情。 */
@Composable
private fun CaptureNoteCard(
    capture: Capture,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 所有卡片统一：一个类型标题 + 完整正文，不再按换行把首行拆成标题
    val title = when (capture.type) {
        CaptureType.IMAGE -> stringResource(R.string.capture_type_image)
        CaptureType.AUDIO -> stringResource(R.string.capture_type_audio)
        CaptureType.TEXT -> stringResource(R.string.journal_card_title_hint)
    }
    val body = capture.displayText

    Surface(
        onClick = onOpen,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row {
                    if (canEdit && capture.type == CaptureType.TEXT) {
                        SmallIconButton(
                            iconRes = R.drawable.ic_edit,
                            description = stringResource(R.string.journal_edit),
                            testTag = "capture_edit_${capture.id}",
                            onClick = onEdit,
                        )
                    }
                    SmallIconButton(
                        iconRes = R.drawable.ic_delete,
                        description = stringResource(R.string.journal_delete_action),
                        testTag = "capture_delete_${capture.id}",
                        onClick = onDelete,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when (capture.type) {
                CaptureType.IMAGE -> ImagePreview(
                    path = capture.mediaPath,
                    modifier = Modifier.fillMaxWidth(),
                    height = 74.dp,
                )

                CaptureType.AUDIO -> AudioPlayer(path = capture.mediaPath)

                CaptureType.TEXT -> Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            if (capture.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    capture.tags.take(2).forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (capture.tags.size > 2) {
                        Text(
                            text = "+${capture.tags.size - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = "${formatShortDate(capture.dayKey)}  ${formatTime(capture.contentAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SmallIconButton(
    iconRes: Int,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageCard(
    message: String,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    VaultCard {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun DeleteCaptureDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_delete_title)) },
        text = { Text(stringResource(R.string.journal_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.journal_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.journal_date_cancel))
            }
        },
    )
}

@Composable
private fun EditCaptureDialog(
    target: Capture,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(target.id) { mutableStateOf(target.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.journal_edit_label)) },
                minLines = 3,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_edit_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("journal_edit_confirm"),
            ) {
                Text(stringResource(R.string.journal_edit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.journal_date_cancel))
            }
        },
    )
}

/**
 * 自己画的月历，而不是系统 DatePicker：可以在有随记的日期下面点一个小圆点。
 * 未来日期不可选，也不能翻到今天之后的月份。
 */
@Composable
private fun JournalCalendarDialog(
    selectedDate: LocalDate,
    markedDays: Set<LocalDate>,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var chosenDate by remember { mutableStateOf(selectedDate) }
    val weekdays = stringArrayResource(R.array.weekday_labels)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_calendar_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { month = month.minusMonths(1) }) {
                        Text("‹", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = month.format(monthFormatter),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = { month = month.plusMonths(1) },
                        enabled = month < YearMonth.from(today),
                    ) {
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                val offset = month.atDay(1).dayOfWeek.value - 1
                val daysInMonth = month.lengthOfMonth()
                val rows = (offset + daysInMonth + 6) / 7

                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (column in 0 until 7) {
                            val dayNumber = row * 7 + column - offset + 1
                            if (dayNumber !in 1..daysInMonth) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                val date = month.atDay(dayNumber)
                                CalendarDay(
                                    date = date,
                                    isSelected = date == chosenDate,
                                    isToday = date == today,
                                    hasRecords = date in markedDays,
                                    isSelectable = !date.isAfter(today),
                                    onClick = { chosenDate = date },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.journal_calendar_marked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDateSelected(chosenDate) }) {
                Text(stringResource(R.string.journal_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.journal_date_cancel))
            }
        },
    )
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasRecords: Boolean,
    isSelectable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = when {
        !isSelectable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dotColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                CircleShape,
            )
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (isSelectable) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(if (hasRecords) dotColor else Color.Transparent, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal class PastOrPresentSelectableDates(
    private val today: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !utcTimeMillis.toUtcLocalDate().isAfter(today)

    override fun isSelectableYear(year: Int): Boolean =
        year <= today.year
}

private val monthFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)

private val dateFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private val shortDateFormatter =
    DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

private val timeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
        .withZone(ZoneId.systemDefault())

private fun formatDate(date: LocalDate): String =
    date.format(dateFormatter)

private fun formatShortDate(date: LocalDate): String =
    date.format(shortDateFormatter)

private fun formatTime(instant: Instant): String =
    timeFormatter.format(instant)

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
