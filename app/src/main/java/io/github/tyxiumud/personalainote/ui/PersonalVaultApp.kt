package io.github.tyxiumud.personalainote.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.tyxiumud.personalainote.R
import io.github.tyxiumud.personalainote.ai.settings.SettingsRoute
import io.github.tyxiumud.personalainote.ai.settings.SettingsViewModel
import io.github.tyxiumud.personalainote.backup.BackupRoute
import io.github.tyxiumud.personalainote.backup.BackupViewModel
import io.github.tyxiumud.personalainote.chat.ui.ChatRoute
import io.github.tyxiumud.personalainote.chat.ui.ChatViewModel
import io.github.tyxiumud.personalainote.journal.ui.JournalRoute
import io.github.tyxiumud.personalainote.journal.ui.JournalViewModel
import io.github.tyxiumud.personalainote.reader.ReaderRoute
import io.github.tyxiumud.personalainote.reader.ReaderViewModel

@Composable
fun PersonalVaultApp(
    journalViewModel: JournalViewModel,
    readerViewModel: ReaderViewModel,
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    backupViewModel: BackupViewModel,
) {
    PersonalVaultApp(
        journalContent = { JournalRoute(journalViewModel) },
        readerContent = { ReaderRoute(readerViewModel) },
        chatContent = { ChatRoute(chatViewModel) },
        settingsContent = { SettingsRoute(settingsViewModel) },
        backupContent = { onDismiss -> BackupRoute(backupViewModel, onDismiss = onDismiss) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalVaultApp(
    journalContent: @Composable () -> Unit,
    readerContent: @Composable () -> Unit,
    chatContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    backupContent: @Composable (() -> Unit) -> Unit = {},
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    val sections = AppSection.entries
    // pagerState 自带 rememberSaveable，当前页在旋转/进程重建后会自动恢复
    val pagerState = rememberPagerState { sections.size }
    val scope = rememberCoroutineScope()
    val selected = sections[pagerState.currentPage]
    // 键盘弹出时由外层统一把页面压到键盘上方，并隐藏底部导航。
    // 若只让聊天页自己吃 IME inset，底部导航仍占位，输入框和键盘之间会空出一段。
    val keyboardVisible = WindowInsets.isImeVisible

    if (showBackup) {
        BottomCard(onDismiss = { showBackup = false }) {
            backupContent { showBackup = false }
        }
    }

    if (showSettings) {
        // 自己控制的底部卡片：Material 的 ModalBottomSheet 在展开态会裁掉标题。
        BottomCard(onDismiss = { showSettings = false }) {
            settingsContent()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 和参考图一样：设置收在右上角，不占主导航。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 24.dp, end = 18.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TopIconButton(
                        iconRes = R.drawable.ic_backup,
                        description = stringResource(R.string.backup_entry),
                        testTag = "backup_entry",
                        onClick = { showBackup = true },
                    )
                    TopIconButton(
                        iconRes = R.drawable.ic_settings_hex,
                        description = stringResource(R.string.nav_settings),
                        testTag = "settings_entry",
                        onClick = { showSettings = true },
                    )
                }
            }
        },
        bottomBar = {
            if (!keyboardVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 14.dp,
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        sections.forEach { section ->
                            NavigationBarItem(
                                selected = section == selected,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(sections.indexOf(section))
                                    }
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(section.icon),
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(section.label),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("main_pager"),
            key = { sections[it].name },
        ) { page ->
            when (sections[page]) {
                AppSection.JOURNAL -> journalContent()
                AppSection.READER -> readerContent()
                AppSection.CHAT -> chatContent()
            }
        }
    }
}

private enum class AppSection(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    JOURNAL(R.string.nav_journal, R.drawable.ic_nav_journal),
    READER(R.string.nav_reader, R.drawable.ic_nav_reader),
    CHAT(R.string.nav_chat, R.drawable.ic_nav_chat),
}

@Composable
private fun TopIconButton(
    iconRes: Int,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(40.dp)
            .testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 设置与备份共用的底部卡片：自带拖拽条，避免 ModalBottomSheet 裁标题。 */
@Composable
private fun BottomCard(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
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
                    .heightIn(max = 660.dp)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp,
            ) {
                Column {
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
                    content()
                }
            }
        }
    }
}
