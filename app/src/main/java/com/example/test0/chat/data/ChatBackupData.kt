package com.example.test0.chat.data

import com.example.test0.chat.model.ChatMessage
import com.example.test0.chat.model.Conversation

/** 备份包里要带的全部聊天数据；会话 id 只在包内有效，导入时会重新分配。 */
data class ChatBackupData(
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
)

/** 会话列表里的派生信息，不写进数据库。 */
data class ConversationPreview(
    val messageCount: Int,
    val lastContent: String,
)

/** 导入聊天数据后实际新增的数量（重复导入会被去重，所以不等于包内数量）。 */
data class ChatRestoreResult(
    val conversationCount: Int,
    val messageCount: Int,
)
