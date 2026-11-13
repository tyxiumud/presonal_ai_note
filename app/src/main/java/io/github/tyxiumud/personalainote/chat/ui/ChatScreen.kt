package io.github.tyxiumud.personalainote.chat.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tyxiumud.personalainote.R
import io.github.tyxiumud.personalainote.chat.data.ConversationPreview
import io.github.tyxiumud.personalainote.chat.model.ChatMessage
import io.github.tyxiumud.personalainote.chat.model.ChatRole
import io.github.tyxiumud.personalainote.chat.model.Conversation
import io.github.tyxiumud.personalainote.journal.model.Capture
import io.github.tyxiumud.personalainote.journal.model.displayText
import io.github.tyxiumud.personalainote.journal.ui.CaptureDetailSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ChatRoute(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        state = state,
        onDraftChanged = viewModel::onDraftChanged,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onClearConversation = viewModel::clearConversation,
        onDeleteMessage = viewModel::deleteMessage,
        onSelectConversation = viewModel::selectConversation,
        onCreateConversation = viewModel::createConversation,
        onDeleteConversation = viewModel::deleteConversation,
        onReferenceClicked = viewModel::onReferenceClicked,
        onDismissReferences = viewModel::dismissReferences,
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onClearConversation: () -> Unit = {},
    onDeleteMessage: (ChatMessage) -> Unit = {},
    onSelectConversation: (Long) -> Unit = {},
    onCreateConversation: () -> Unit = {},
    onDeleteConversation: (Long) -> Unit = {},
    onReferenceClicked: (ChatMessage) -> Unit = {},
    onDismissReferences: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showConversations by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var pendingConversationDelete by remember { mutableStateOf<Conversation?>(null) }
    var selectedReference by remember { mutableStateOf<Capture?>(null) }
    val currentTitle = state.conversations
        .firstOrNull { it.id == state.currentConversationId }
        ?.title
        ?: stringResource(R.string.chat_conversations)

    if (showConversations) {
        ConversationSheet(
            conversations = state.conversations,
            currentId = state.currentConversationId,
            previews = state.previews,
            onSelect = {
                onSelectConversation(it)
                showConversations = false
            },
            onCreate = {
                onCreateConversation()
                showConversations = false
            },
            onDelete = { pendingConversationDelete = it },
            onDismiss = { showConversations = false },
        )
    }

    state.referenceSheet?.let { captures ->
        ReferenceSheet(
            captures = captures,
            onSelect = { capture ->
                onDismissReferences()
                selectedReference = capture
            },
            onDismiss = onDismissReferences,
        )
    }

    selectedReference?.let { capture ->
        CaptureDetailSheet(
            capture = capture,
            onDismiss = { selectedReference = null },
        )
    }

    pendingConversationDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingConversationDelete = null },
            title = { Text(stringResource(R.string.chat_delete_conversation_title)) },
            text = { Text(stringResource(R.string.chat_delete_conversation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConversation(target.id)
                        pendingConversationDelete = null
                    },
                    modifier = Modifier.testTag("chat_delete_conversation_confirm"),
                ) {
                    Text(stringResource(R.string.chat_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConversationDelete = null }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMessage(target)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag("chat_delete_confirm"),
                ) {
                    Text(stringResource(R.string.chat_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.chat_clear_title)) },
            text = { Text(stringResource(R.string.chat_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearConversation()
                        showClearDialog = false
                    },
                    modifier = Modifier.testTag("chat_clear_confirm"),
                ) {
                    Text(stringResource(R.string.chat_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }

    LaunchedEffect(state.messages.size, state.isSending) {
        val itemCount = state.messages.size + if (state.isSending) 1 else 0
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    // IME inset 由外层 PersonalVaultApp 统一处理（键盘弹出时整页上移、隐藏底部导航）。
    // 这里不再重复加 imePadding()，否则输入框和键盘之间会空出一段。
    // contentWindowInsets 只取左右 safeDrawing，避免底部 inset 叠加。
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        bottomBar = {
            ChatComposer(
                draft = state.draft,
                canSend = state.canSend,
                errorMessage = state.errorMessage,
                canRetry = state.canRetry,
                onDraftChanged = onDraftChanged,
                onSend = onSend,
                onRetry = onRetry,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("chat_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ChatHeader(
                    showClear = state.messages.isNotEmpty() && !state.isSending,
                    conversationTitle = currentTitle,
                    onClearClick = { showClearDialog = true },
                    onConversationsClick = { showConversations = true },
                )
            }

            if (state.messages.isEmpty() && !state.isSending) {
                item {
                    EmptyChat(
                        onSuggestionSelected = onSuggestionSelected,
                    )
                }
            }

            items(state.messages, key = ChatMessage::id) { message ->
                MessageBubble(
                    message = message,
                    longPressEnabled = !state.isSending,
                    onLongPress = { pendingDelete = message },
                    onReferenceClick = { onReferenceClicked(message) },
                )
            }

            if (state.isSending) {
                item { ThinkingBubble() }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    showClear: Boolean,
    conversationTitle: String,
    onClearClick: () -> Unit,
    onConversationsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onConversationsClick,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.testTag("chat_conversations"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_conversations),
                        contentDescription = stringResource(R.string.chat_conversations),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = conversationTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 88.dp),
                    )
                }
            }
            if (showClear) {
                TextButton(
                    onClick = onClearClick,
                    modifier = Modifier.testTag("chat_clear"),
                ) {
                    Text(
                        text = stringResource(R.string.chat_clear),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.chat_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyChat(onSuggestionSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_chat),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.chat_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            SUGGESTIONS.forEach { suggestion ->
                val label = stringResource(suggestion)
                SuggestionChip(
                    onClick = { onSuggestionSelected(label) },
                    label = { Text(label) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    longPressEnabled: Boolean,
    onLongPress: () -> Unit,
    onReferenceClick: () -> Unit,
) {
    val isUser = message.role == ChatRole.USER
    val shape = if (isUser) {
        RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    } else {
        RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    }
    // 新消息淡入并轻轻上浮，避免"啪"地一下贴出来。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "bubble_entrance",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 28f
                    scaleX = 0.96f + 0.04f * progress
                    scaleY = 0.96f + 0.04f * progress
                },
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Surface(
                modifier = Modifier
                    .clip(shape)
                    .combinedClickable(
                        enabled = longPressEnabled,
                        onClick = {},
                        onLongClick = onLongPress,
                    )
                    .testTag("chat_message_${message.id}"),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                shape = shape,
                border = if (isUser) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (!isUser) {
                ReferenceLabel(message, onClick = onReferenceClick)
            }
        }
    }
}

@Composable
private fun ReferenceLabel(
    message: ChatMessage,
    onClick: () -> Unit,
) {
    val days = message.referencedDays
    val label = if (days.isEmpty()) {
        stringResource(R.string.chat_no_references)
    } else {
        stringResource(
            R.string.chat_references,
            days.joinToString("、") { formatDay(it) },
        )
    }
    Row(
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message.referencedCaptureIds.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.chat_view_references),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .testTag("chat_view_references_${message.id}"),
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThinkingDots()
                Text(
                    text = stringResource(R.string.chat_thinking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 520,
                        delayMillis = index * 130,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "thinking_dot_$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    canSend: Boolean,
    errorMessage: String?,
    canRetry: Boolean,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (canRetry) {
                        TextButton(onClick = onRetry, modifier = Modifier.testTag("chat_retry")) {
                            Text(stringResource(R.string.chat_retry))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                )
                Box(modifier = Modifier.padding(bottom = 4.dp)) {
                    Button(
                        onClick = onSend,
                        enabled = canSend,
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("chat_send"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_send),
                            contentDescription = stringResource(R.string.chat_send),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

private val SUGGESTIONS = listOf(
    R.string.chat_suggestion_1,
    R.string.chat_suggestion_2,
    R.string.chat_suggestion_3,
)

private val dayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

private fun formatDay(day: LocalDate): String = day.format(dayFormatter)

/** 和设置、备份一样的底部卡片：自己控制 Dialog，避免 ModalBottomSheet 裁掉标题。 */
@Composable
private fun ConversationSheet(
    conversations: List<Conversation>,
    currentId: Long,
    previews: Map<Long, ConversationPreview>,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    onDelete: (Conversation) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp,
            ) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.chat_conversations_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.chat_conversations_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = onCreate,
                            modifier = Modifier.testTag("chat_new_conversation"),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.chat_new_conversation))
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("chat_conversation_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(conversations, key = Conversation::id) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                isCurrent = conversation.id == currentId,
                                preview = previews[conversation.id],
                                onSelect = { onSelect(conversation.id) },
                                onDelete = { onDelete(conversation) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    isCurrent: Boolean,
    preview: ConversationPreview?,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val currentLabel = stringResource(R.string.chat_current_conversation)
    val emptyLabel = stringResource(R.string.chat_no_messages)
    val countLabel = stringResource(
        R.string.chat_message_count,
        preview?.messageCount ?: 0,
    )
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_conversation_${conversation.id}"),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        preview == null || preview.messageCount == 0 ->
                            if (isCurrent) "$currentLabel · $emptyLabel" else emptyLabel
                        else -> buildString {
                            if (isCurrent) append("$currentLabel · ")
                            append(countLabel)
                            append(" · ")
                            append(preview.lastContent.replace('\n', ' ').trim())
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("chat_delete_conversation_${conversation.id}"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.chat_conversation_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 点「查看原文」后的底部卡片：列出这次回答真正引用过的原始随记。 */
@Composable
private fun ReferenceSheet(
    captures: List<Capture>,
    onSelect: (Capture) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp,
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.chat_references_sheet_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.chat_references_sheet_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (captures.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_references_deleted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("chat_reference_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(captures, key = Capture::id) { capture ->
                                val blankLabel = stringResource(R.string.chat_reference_blank)
                                Surface(
                                    onClick = { onSelect(capture) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                        .copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 12.dp,
                                        ),
                                    ) {
                                        Text(
                                            text = formatDay(capture.dayKey),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = capture.displayText.ifBlank { blankLabel },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
