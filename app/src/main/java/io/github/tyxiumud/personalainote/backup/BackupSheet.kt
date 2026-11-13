package io.github.tyxiumud.personalainote.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tyxiumud.personalainote.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BackupRoute(
    viewModel: BackupViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(viewModel::import) }

    BackupSheet(
        state = state,
        onDismiss = onDismiss,
        onToggleDay = viewModel::toggleDay,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onExport = { exportLauncher.launch("personal-ai-notes-backup.zip") },
        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSheet(
    state: BackupUiState,
    onDismiss: () -> Unit,
    onToggleDay: (LocalDate) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.backup_title),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            text = stringResource(R.string.backup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (state.days.isEmpty()) {
                            Text(
                                text = stringResource(R.string.backup_no_days),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.backup_selected,
                                        state.selectedDays.size,
                                        state.days.size,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Row {
                                    TextButton(onClick = onSelectAll) {
                                        Text(stringResource(R.string.backup_select_all))
                                    }
                                    TextButton(onClick = onClearSelection) {
                                        Text(stringResource(R.string.backup_clear))
                                    }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.days.forEach { day ->
                                    FilterChip(
                                        selected = day in state.selectedDays,
                                        onClick = { onToggleDay(day) },
                                        label = { Text(formatDay(day)) },
                                        modifier = Modifier.testTag("backup_day_$day"),
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.backup_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (state.isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        if (state.message != null) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("backup_message"),
                            )
                        }

                        Button(
                            onClick = onExport,
                            enabled = state.canExport,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_export"),
                        ) {
                            Text(stringResource(R.string.backup_export))
                        }
                        OutlinedButton(
                            onClick = onImport,
                            enabled = !state.isWorking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_import"),
                        ) {
                            Text(stringResource(R.string.backup_import))
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.capture_close))
                        }
                    }
                }
            }
        }
    }
}

private val backupDateFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private fun formatDay(day: LocalDate): String = day.format(backupDateFormatter)
