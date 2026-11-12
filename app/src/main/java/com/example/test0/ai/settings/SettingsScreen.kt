package com.example.test0.ai.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.test0.R
import com.example.test0.ui.components.VaultCard

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onModelChanged = viewModel::onModelChanged,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onIncludeTasksChanged = viewModel::onIncludeTasksChanged,
        onIncludeSuggestionsChanged = viewModel::onIncludeSuggestionsChanged,
        onIncludeReflectionsChanged = viewModel::onIncludeReflectionsChanged,
        onPersonaChanged = viewModel::onPersonaChanged,
        onAllowEditingChanged = viewModel::onAllowEditingChanged,
        onSkinChanged = viewModel::onSkinChanged,
        onVisionUseLlmChanged = viewModel::onVisionUseLlmChanged,
        onVisionBaseUrlChanged = viewModel::onVisionBaseUrlChanged,
        onVisionModelChanged = viewModel::onVisionModelChanged,
        onVisionApiKeyChanged = viewModel::onVisionApiKeyChanged,
        onTranscriptionBaseUrlChanged = viewModel::onTranscriptionBaseUrlChanged,
        onTranscriptionModelChanged = viewModel::onTranscriptionModelChanged,
        onTranscriptionApiKeyChanged = viewModel::onTranscriptionApiKeyChanged,
        onSave = viewModel::save,
        onTestConnection = viewModel::testConnection,
        onClearApiKey = viewModel::clearApiKey,
        onClearVisionApiKey = viewModel::clearVisionApiKey,
        onClearTranscriptionApiKey = viewModel::clearTranscriptionApiKey,
        modifier = modifier,
    )
}

/** 内容本身，不含 Scaffold：既可以直接铺满页面，也可以放进底部弹层。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onIncludeTasksChanged: (Boolean) -> Unit,
    onIncludeSuggestionsChanged: (Boolean) -> Unit,
    onIncludeReflectionsChanged: (Boolean) -> Unit,
    onPersonaChanged: (ChatPersona) -> Unit = {},
    onAllowEditingChanged: (Boolean) -> Unit = {},
    onSkinChanged: (AppSkin) -> Unit = {},
    onVisionUseLlmChanged: (Boolean) -> Unit = {},
    onVisionBaseUrlChanged: (String) -> Unit = {},
    onVisionModelChanged: (String) -> Unit = {},
    onVisionApiKeyChanged: (String) -> Unit = {},
    onTranscriptionBaseUrlChanged: (String) -> Unit = {},
    onTranscriptionModelChanged: (String) -> Unit = {},
    onTranscriptionApiKeyChanged: (String) -> Unit = {},
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onClearApiKey: () -> Unit,
    onClearVisionApiKey: () -> Unit = {},
    onClearTranscriptionApiKey: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        VaultCard {
            SettingsSectionHeader(
                iconRes = R.drawable.ic_nav_settings,
                text = stringResource(R.string.settings_connection),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text(stringResource(R.string.settings_base_url)) },
                placeholder = { Text("https://api.example.com/v1") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_base_url"),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.model,
                onValueChange = onModelChanged,
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_model"),
            )
            if (state.modelPresets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.modelPresets.forEach { preset ->
                        FilterChip(
                            selected = state.model == preset,
                            onClick = { onModelChanged(preset) },
                            label = { Text(preset) },
                            modifier = Modifier.testTag("settings_model_preset_$preset"),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.apiKeyDraft,
                onValueChange = onApiKeyChanged,
                label = {
                    Text(
                        if (state.hasApiKey) {
                            stringResource(R.string.settings_api_key_saved)
                        } else {
                            stringResource(R.string.settings_api_key)
                        },
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_api_key"),
            )
            if (state.hasApiKey) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_api_key_saved_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        VaultCard {
            SettingsSectionHeader(
                iconRes = R.drawable.ic_nav_chat,
                text = stringResource(R.string.settings_persona_title),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_persona_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChatPersona.entries.forEach { persona ->
                    FilterChip(
                        selected = state.persona == persona,
                        onClick = { onPersonaChanged(persona) },
                        label = { Text(persona.label) },
                        modifier = Modifier.testTag("settings_persona_${persona.name}"),
                    )
                }
            }
        }

        VaultCard {
            SettingsSectionHeader(
                iconRes = R.drawable.ic_sparkle,
                text = stringResource(R.string.settings_skin_title),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_skin_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSkin.entries.forEach { skin ->
                    SkinOption(
                        skin = skin,
                        selected = state.skin == skin,
                        onClick = { onSkinChanged(skin) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_skin_${skin.name}"),
                    )
                }
            }
        }

        VaultCard {
            SettingsSectionHeader(
                iconRes = R.drawable.ic_edit,
                text = stringResource(R.string.settings_editing_title),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_editing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingSwitch(
                text = stringResource(R.string.settings_allow_editing),
                checked = state.allowEditing,
                onCheckedChange = onAllowEditingChanged,
                testTag = "settings_allow_editing",
            )
        }

        VaultCard {
            SettingsSectionHeader(
                iconRes = R.drawable.ic_image,
                text = stringResource(R.string.settings_media_title),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_media_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── 图片识别 ──
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_vision_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingSwitch(
                text = stringResource(R.string.settings_media_same_as_llm),
                checked = state.visionUseLlm,
                onCheckedChange = onVisionUseLlmChanged,
                testTag = "settings_vision_use_llm",
            )
            if (!state.visionUseLlm) {
                ConnectionFields(
                    baseUrl = state.visionBaseUrl,
                    onBaseUrlChanged = onVisionBaseUrlChanged,
                    baseUrlTag = "settings_vision_base_url",
                    model = state.visionModel,
                    onModelChanged = onVisionModelChanged,
                    modelLabel = R.string.settings_vision_model,
                    modelHint = R.string.settings_vision_hint,
                    modelTag = "settings_vision_model",
                    apiKeyDraft = state.visionApiKeyDraft,
                    onApiKeyChanged = onVisionApiKeyChanged,
                    hasApiKey = state.hasVisionApiKey,
                    apiKeyTag = "settings_vision_api_key",
                    onClearApiKey = onClearVisionApiKey,
                    clearTag = "settings_vision_clear_key",
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_media_same_as_llm_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.visionModel,
                    onValueChange = onVisionModelChanged,
                    label = { Text(stringResource(R.string.settings_vision_model)) },
                    placeholder = { Text(stringResource(R.string.settings_vision_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_vision_model"),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // ── 语音转写 ──
            Text(
                text = stringResource(R.string.settings_audio_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            ConnectionFields(
                baseUrl = state.transcriptionBaseUrl,
                onBaseUrlChanged = onTranscriptionBaseUrlChanged,
                baseUrlTag = "settings_audio_base_url",
                model = state.transcriptionModel,
                onModelChanged = onTranscriptionModelChanged,
                modelLabel = R.string.settings_transcription_model,
                modelHint = R.string.settings_transcription_model,
                modelTag = "settings_transcription_model",
                apiKeyDraft = state.transcriptionApiKeyDraft,
                onApiKeyChanged = onTranscriptionApiKeyChanged,
                hasApiKey = state.hasTranscriptionApiKey,
                apiKeyTag = "settings_audio_api_key",
                onClearApiKey = onClearTranscriptionApiKey,
                clearTag = "settings_audio_clear_key",
            )
        }

        VaultCard {
            SettingsSectionHeader(
                iconRes = R.drawable.ic_sparkle,
                text = stringResource(R.string.settings_output_title),
            )
            Spacer(Modifier.height(6.dp))
            SettingSwitch(
                text = stringResource(R.string.settings_include_tasks),
                checked = state.includeTasks,
                onCheckedChange = onIncludeTasksChanged,
            )
            SettingSwitch(
                text = stringResource(R.string.settings_include_suggestions),
                checked = state.includeSuggestions,
                onCheckedChange = onIncludeSuggestionsChanged,
            )
            SettingSwitch(
                text = stringResource(R.string.settings_include_reflections),
                checked = state.includeReflections,
                onCheckedChange = onIncludeReflectionsChanged,
            )
        }

        Text(
            text = stringResource(R.string.settings_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (state.message != null) {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("settings_message"),
            )
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_save"),
        ) {
            Text(
                if (state.isSaving) {
                    stringResource(R.string.settings_saving)
                } else {
                    stringResource(R.string.settings_save)
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onTestConnection,
                enabled = !state.isTesting && !state.isSaving,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_test_connection"),
            ) {
                Text(
                    if (state.isTesting) {
                        stringResource(R.string.settings_testing)
                    } else {
                        stringResource(R.string.settings_test_connection)
                    },
                )
            }
            if (state.hasApiKey) {
                OutlinedButton(
                    onClick = onClearApiKey,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_clear_key"),
                ) {
                    Text(stringResource(R.string.settings_clear_key))
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    iconRes: Int,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 独立接口的地址 / 模型 / 密钥三件套；密钥留空表示不更换已保存的值。 */
@Composable
private fun ConnectionFields(
    baseUrl: String,
    onBaseUrlChanged: (String) -> Unit,
    baseUrlTag: String,
    model: String,
    onModelChanged: (String) -> Unit,
    modelLabel: Int,
    modelHint: Int,
    modelTag: String,
    apiKeyDraft: String,
    onApiKeyChanged: (String) -> Unit,
    hasApiKey: Boolean,
    apiKeyTag: String,
    onClearApiKey: () -> Unit,
    clearTag: String,
) {
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChanged,
        label = { Text(stringResource(R.string.settings_media_base_url)) },
        placeholder = { Text(stringResource(R.string.settings_media_base_url_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(baseUrlTag),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = model,
        onValueChange = onModelChanged,
        label = { Text(stringResource(modelLabel)) },
        placeholder = { Text(stringResource(modelHint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(modelTag),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = apiKeyDraft,
        onValueChange = onApiKeyChanged,
        label = {
            Text(
                if (hasApiKey) {
                    stringResource(R.string.settings_media_api_key_saved)
                } else {
                    stringResource(R.string.settings_media_api_key)
                },
            )
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(apiKeyTag),
    )
    if (hasApiKey) {
        TextButton(
            onClick = onClearApiKey,
            modifier = Modifier.testTag(clearTag),
        ) {
            Text(stringResource(R.string.settings_media_clear_key))
        }
    }
}

@Composable
private fun SettingSwitch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (testTag != null) Modifier.testTag(testTag) else Modifier,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SkinOption(
    skin: AppSkin,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (background, accent) = when (skin) {
        AppSkin.PAPER -> Color(0xFFF7F4ED) to Color(0xFF1F4D3F)
        AppSkin.MINT -> Color(0xFFF1F8F5) to Color(0xFF166B5B)
        AppSkin.ROSE -> Color(0xFFFBF5F4) to Color(0xFF8A4A5B)
        AppSkin.NIGHT -> Color(0xFF111613) to Color(0xFF9FD3B7)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = background,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(accent, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = skin.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
