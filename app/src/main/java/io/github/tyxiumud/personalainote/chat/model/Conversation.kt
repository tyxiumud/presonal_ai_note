package io.github.tyxiumud.personalainote.chat.model

import java.time.Instant

/** 全新安装与旧版本升级时都会有一个 id 为 1 的默认会话，旧消息归到这里。 */
const val DEFAULT_CONVERSATION_ID = 1L

const val DEFAULT_CONVERSATION_TITLE = "默认对话"

/** 新建但还没提问的会话用「新对话 N」，避免列表里出现好几个同名的空会话。 */
const val NEW_CONVERSATION_PREFIX = "新对话"

fun isPlaceholderConversationTitle(title: String): Boolean =
    title == DEFAULT_CONVERSATION_TITLE || title.startsWith(NEW_CONVERSATION_PREFIX)

data class Conversation(
    val id: Long = 0,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** 用第一条提问给新会话起名，长问题截断，避免列表里挤成一团。 */
fun conversationTitleFrom(question: String): String {
    val normalized = question.trim().replace(Regex("\\s+"), " ")
    return if (normalized.length <= 14) normalized else normalized.take(14) + "…"
}
