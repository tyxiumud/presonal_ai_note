package com.example.test0.ai.settings

/** 聊天时的预设人格。只影响提示词措辞，不改变检索与引用逻辑。 */
enum class ChatPersona(
    val label: String,
    val instruction: String,
) {
    WARM(
        label = "温暖陪伴",
        instruction = """
            你像一位认真读过这些记录的老朋友，语气温和、不急不慢。
            可以先轻轻接住对方这句话里的情绪，再谈记录里的事实。
            不用套话开头，不说教，不连续追问，也不写鼓励式鸡汤。
        """.trimIndent(),
    ),
    CONCISE(
        label = "简洁清晰",
        instruction = """
            你表达克制、直奔重点，先给结论再给依据。
            句子短，少用形容词，不加寒暄和客套。
        """.trimIndent(),
    ),
    BRIGHT(
        label = "轻快活泼",
        instruction = """
            你语气轻快，有一点幽默感，但不浮夸。
            可以用短句和轻松的比喻，让回看记录这件事不显得沉闷。
        """.trimIndent(),
    ),
}

/** 外观皮肤：只改配色，不改数据与行为。 */
enum class AppSkin(val label: String) {
    PAPER("暖纸"),
    MINT("薄荷"),
    ROSE("藕粉"),
    NIGHT("夜色"),
}

data class AiSettingsSnapshot(
    val baseUrl: String = "",
    val model: String = "",
    val hasApiKey: Boolean = false,
    val includeTasks: Boolean = true,
    val includeSuggestions: Boolean = true,
    val includeReflections: Boolean = true,
    val persona: ChatPersona = ChatPersona.WARM,
    val allowEditing: Boolean = false,
    val skin: AppSkin = AppSkin.PAPER,
    /** 图片识别：勾选后直接复用聊天接口的地址、模型和密钥。 */
    val visionUseLlm: Boolean = true,
    val visionBaseUrl: String = "",
    val hasVisionApiKey: Boolean = false,
    /** 留空表示复用聊天模型；图片描述需要该模型支持视觉输入。 */
    val visionModel: String = "",
    /** 语音转写：留空地址或密钥时沿用聊天连接，模型单独指定。 */
    val transcriptionBaseUrl: String = "",
    val hasTranscriptionApiKey: Boolean = false,
    val transcriptionModel: String = "whisper-1",
)

data class AiSettingsInput(
    val baseUrl: String,
    val model: String,
    val newApiKey: String? = null,
    val includeTasks: Boolean = true,
    val includeSuggestions: Boolean = true,
    val includeReflections: Boolean = true,
    val persona: ChatPersona = ChatPersona.WARM,
    val allowEditing: Boolean = false,
    val skin: AppSkin = AppSkin.PAPER,
    val visionUseLlm: Boolean = true,
    val visionBaseUrl: String = "",
    val newVisionApiKey: String? = null,
    val visionModel: String = "",
    val transcriptionBaseUrl: String = "",
    val newTranscriptionApiKey: String? = null,
    val transcriptionModel: String = "whisper-1",
)

data class AiConnectionSettings(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)
