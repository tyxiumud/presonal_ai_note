package io.github.tyxiumud.personalainote.journal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tyxiumud.personalainote.R

/** 标签区：显示已有标签、点 chip 移除、通过对话框新增；预置几个常用标签可一键添加。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSection(
    tags: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    /** 同一屏出现两个标签区时用它区分测试 tag。 */
    testTagPrefix: String = "",
) {
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val predefined = listOf(
        stringResource(R.string.tag_predefined_work),
        stringResource(R.string.tag_predefined_life),
        stringResource(R.string.tag_predefined_idea),
        stringResource(R.string.tag_predefined_todo),
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.tag_section)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.replace(",", "") },
                        label = { Text(stringResource(R.string.tag_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${testTagPrefix}tag_input"),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        predefined.filter { it !in tags }.forEach { label ->
                            AssistChip(
                                onClick = {
                                    onChange(tags + label)
                                    showDialog = false
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = draft.trim()
                        if (value.isNotEmpty() && value !in tags) {
                            onChange(tags + value)
                        }
                        draft = ""
                        showDialog = false
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("${testTagPrefix}tag_confirm"),
                ) {
                    Text(stringResource(R.string.tag_add_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.journal_date_cancel))
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_tag),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.tag_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
            TextButton(
                onClick = { showDialog = true },
                modifier = Modifier.testTag("${testTagPrefix}tag_add"),
            ) {
                Text(stringResource(R.string.tag_add))
            }
        }
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.tag_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onChange(tags - tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_close_small),
                                contentDescription = stringResource(R.string.tag_remove, tag),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.testTag("${testTagPrefix}tag_chip_$tag"),
                    )
                }
            }
        }
    }
}
