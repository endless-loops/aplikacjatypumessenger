package com.example.aplikacjatypumessenger

import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.models.MessageStatus
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.models.UserStatus
import com.example.aplikacjatypumessenger.viewmodels.buildChatListItems
import com.example.aplikacjatypumessenger.viewmodels.filterUsers
import com.example.aplikacjatypumessenger.viewmodels.toggle
import com.example.aplikacjatypumessenger.viewmodels.unreadIncomingMessageIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMappersTest {

    private val me = "me"
    private val anna = User(id = "anna", username = "Anna", status = UserStatus.ONLINE)
    private val bartek = User(id = "bartek", username = "Bartek", status = UserStatus.OFFLINE)
    private val usersById = listOf(anna, bartek).associateBy { it.id }

    @Test
    fun `private chat takes the other participant's name`() {
        val chat = Chat(id = "c1", participants = listOf(me, "anna"))

        val item = buildChatListItems(listOf(chat), usersById, me).single()

        assertEquals("Anna", item.title)
        assertEquals("anna", item.otherUserId)
    }

    @Test
    fun `group chat takes the group name`() {
        val chat = Chat(id = "g1", participants = listOf(me, "anna", "bartek"), isGroup = true, groupName = "Paczka")

        val item = buildChatListItems(listOf(chat), usersById, me).single()

        assertEquals("Paczka", item.title)
        assertNull(item.otherUserId)
    }

    @Test
    fun `chat with no other participant does not crash`() {
        val chat = Chat(id = "c1", participants = listOf(me))

        val item = buildChatListItems(listOf(chat), usersById, me).single()

        assertNull(item.title)
        assertNull(item.otherUserId)
    }

    @Test
    fun `chats are sorted by latest activity and deduplicated`() {
        val old = Chat(id = "old", participants = listOf(me, "anna"), createdAt = 100)
        val recent = Chat(
            id = "recent",
            participants = listOf(me, "bartek"),
            lastMessage = Message(text = "hej", timestamp = 500),
            lastMessageTime = 500
        )
        val newEmpty = Chat(id = "new", participants = listOf(me, "anna"), createdAt = 300)

        val ids = buildChatListItems(listOf(old, recent, newEmpty, old), usersById, me).map { it.chatId }

        assertEquals(listOf("recent", "new", "old"), ids)
    }

    @Test
    fun `filterUsers hides current user, matches query and puts online users first`() {
        val users = listOf(bartek, User(id = me, username = "Ja"), anna)

        assertEquals(listOf("anna", "bartek"), filterUsers(users, me, "").map { it.id })
        assertEquals(listOf("bartek"), filterUsers(users, me, " bar ").map { it.id })
    }

    @Test
    fun `only incoming, saved and unread messages are marked as read`() {
        val messages = listOf(
            Message(id = "1", senderId = "anna", status = MessageStatus.SENT),
            Message(id = "2", senderId = "anna", status = MessageStatus.DELIVERED),
            Message(id = "3", senderId = "anna", status = MessageStatus.READ),
            Message(id = "4", senderId = me, status = MessageStatus.SENT),
            Message(id = "5", senderId = "anna", status = MessageStatus.SENDING)
        )

        assertEquals(listOf("1", "2"), unreadIncomingMessageIds(messages, me))
    }

    @Test
    fun `toggle adds and removes selection`() {
        assertEquals(setOf("a", "b"), setOf("a").toggle("b"))
        assertEquals(setOf("a"), setOf("a", "b").toggle("b"))
    }
}
