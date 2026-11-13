package io.github.tyxiumud.personalainote.chat.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tyxiumud.personalainote.chat.model.ChatMessage
import io.github.tyxiumud.personalainote.chat.model.ChatRole
import io.github.tyxiumud.personalainote.journal.data.PersonalVaultDatabase
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatConversationRepositoryTest {
    private lateinit var database: PersonalVaultDatabase
    private lateinit var repository: RoomChatRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, PersonalVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomChatRepository(database.chatMessageDao(), database.conversationDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun messagesAreScopedToConversations() = runBlocking {
        val first = repository.createConversation()
        val second = repository.createConversation()
        val now = Instant.parse("2026-11-08T09:00:00Z")
        repository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "第一个话题",
                createdAt = now,
                conversationId = first.id,
            ),
        )
        repository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "第二个话题",
                createdAt = now,
                conversationId = second.id,
            ),
        )

        assertEquals(
            listOf("第一个话题"),
            repository.observeMessages(first.id).first().map(ChatMessage::content),
        )
        assertEquals(
            listOf("第二个话题"),
            repository.observeMessages(second.id).first().map(ChatMessage::content),
        )

        repository.clear(first.id)

        assertTrue(repository.observeMessages(first.id).first().isEmpty())
        assertEquals(
            listOf("第二个话题"),
            repository.observeMessages(second.id).first().map(ChatMessage::content),
        )
    }

    @Test
    fun firstQuestionNamesConversationAndDeletingRemovesItsMessages() = runBlocking {
        val conversation = repository.createConversation()
        repository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "我在研究什么",
                createdAt = Instant.parse("2026-11-08T09:00:00Z"),
                conversationId = conversation.id,
            ),
        )

        assertEquals(
            "我在研究什么",
            repository.conversations().single { it.id == conversation.id }.title,
        )

        repository.deleteConversation(conversation.id)

        assertTrue(repository.conversations().none { it.id == conversation.id })
        assertTrue(repository.observeMessages(conversation.id).first().isEmpty())
    }
}
