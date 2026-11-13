package io.github.tyxiumud.personalainote.ai.settings

import kotlinx.coroutines.flow.StateFlow

interface AiSettingsStore {
    val settings: StateFlow<AiSettingsSnapshot>

    suspend fun save(input: AiSettingsInput)

    suspend fun connection(): AiConnectionSettings?

    /** 图片识别使用的连接；默认等同聊天连接，真实存储会按独立配置覆盖。 */
    suspend fun visionConnection(): AiConnectionSettings? = connection()

    /** 语音转写使用的连接；默认等同聊天连接，真实存储会按独立配置覆盖。 */
    suspend fun transcriptionConnection(): AiConnectionSettings? = connection()

    suspend fun clearApiKey()

    /** 测试替身不需要真正删除，给默认空实现，真实存储会覆盖。 */
    suspend fun clearVisionApiKey() {}

    suspend fun clearTranscriptionApiKey() {}

    /** 单独保存编辑开关，不需要先配置 API。 */
    suspend fun setAllowEditing(value: Boolean)

    /** 单独保存外观皮肤，不需要先配置 API。 */
    suspend fun setSkin(skin: AppSkin)
}
