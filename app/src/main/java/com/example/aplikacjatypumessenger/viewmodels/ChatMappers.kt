package com.example.aplikacjatypumessenger.viewmodels

import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.models.MessageStatus
import com.example.aplikacjatypumessenger.models.User

/** Element listy konwersacji. `title == null` oznacza nieznaną nazwę (UI pokazuje tekst zastępczy). */
data class ChatListItem(
    val chatId: String,
    val title: String?,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val isGroup: Boolean,
    val otherUserId: String?
)

/** Wiadomość przygotowana do wyświetlenia. */
data class MessageItem(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val status: String,
    val senderName: String? = null
)

fun buildChatListItems(chats: List<Chat>, usersById: Map<String, User>, currentUserId: String): List<ChatListItem> =
    chats.distinctBy { it.id }
        .map { chat ->
            val otherUserId = if (chat.isGroup) null else chat.participants.firstOrNull { it != currentUserId }
            ChatListItem(
                chatId = chat.id,
                title = if (chat.isGroup) chat.groupName.ifBlank { null } else otherUserId?.let { usersById[it]?.username },
                lastMessage = chat.lastMessage?.text,
                lastMessageTime = chat.lastMessage?.timestamp?.takeIf { it > 0 } ?: chat.lastMessageTime,
                isGroup = chat.isGroup,
                otherUserId = otherUserId
            ) to (chat.lastMessageTime.takeIf { it > 0 } ?: chat.createdAt)
        }
        .sortedByDescending { (item, fallbackTime) -> maxOf(item.lastMessageTime, fallbackTime) }
        .map { it.first }

/** Inni użytkownicy pasujący do wyszukiwania; najpierw dostępni, potem alfabetycznie. */
fun filterUsers(users: List<User>, currentUserId: String, query: String): List<User> {
    val trimmed = query.trim()
    return users.asSequence()
        .filter { it.id != currentUserId }
        .distinctBy { it.id }
        .filter { trimmed.isEmpty() || it.username.contains(trimmed, ignoreCase = true) || it.email.contains(trimmed, ignoreCase = true) }
        .sortedWith(compareByDescending<User> { it.isOnline }.thenBy { it.username.lowercase() })
        .toList()
}

/** Id wiadomości od innych osób, które trzeba oznaczyć jako przeczytane. */
fun unreadIncomingMessageIds(messages: List<Message>, currentUserId: String): List<String> =
    messages.filter {
        it.senderId != currentUserId && it.status != MessageStatus.READ && it.status != MessageStatus.SENDING
    }.map { it.id }

fun Message.toItem(currentUserId: String, senderName: String? = null): MessageItem =
    MessageItem(
        id = id,
        text = text,
        timestamp = timestamp,
        isMine = senderId == currentUserId,
        status = status,
        senderName = senderName
    )
