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
        it.senderId != currentUserId && it.status != MessageStatus.SENDING && !it.isReadBy(currentUserId)
    }.map { it.id }

/**
 * Wiadomość grupowa z listą odbiorców jest przeczytana przez danego użytkownika, gdy jest w `readBy`
 * (osoba spoza listy odbiorców nie ma czego oznaczać). Starsze wiadomości korzystają z pola `status`.
 */
private fun Message.isReadBy(userId: String): Boolean =
    if (recipients.isNotEmpty()) userId !in recipients || userId in readBy else status == MessageStatus.READ

/** Kto spośród odbiorców wciąż należących do grupy przeczytał wiadomość, a kto jeszcze nie. */
data class ReadReceipts(val readBy: List<String>, val notReadBy: List<String>) {
    val isReadByAll: Boolean get() = readBy.isNotEmpty() && notReadBy.isEmpty()
}

/** `null` dla wiadomości bez listy odbiorców (czat prywatny albo starsza wiadomość grupowa). */
fun Message.readReceipts(currentMembers: Collection<String>): ReadReceipts? {
    if (recipients.isEmpty()) return null
    val required = recipients.filter { it in currentMembers }
    return ReadReceipts(
        readBy = required.filter { it in readBy },
        notReadBy = required.filterNot { it in readBy }
    )
}

/**
 * Status widoczny dla nadawcy. W grupie "przeczytana" oznacza przeczytaną przez wszystkich obecnych
 * odbiorców; jeśli przeczytała tylko część, wiadomość wygląda na dostarczoną.
 */
fun Message.displayStatus(currentMembers: Collection<String>?): String {
    if (status == MessageStatus.SENDING || currentMembers == null) return status
    val receipts = readReceipts(currentMembers) ?: return status
    return when {
        receipts.isReadByAll -> MessageStatus.READ
        status == MessageStatus.READ || receipts.readBy.isNotEmpty() -> MessageStatus.DELIVERED
        else -> status
    }
}

/** @param currentMembers aktualni członkowie grupy; `null` w czacie prywatnym */
fun Message.toItem(
    currentUserId: String,
    senderName: String? = null,
    currentMembers: Collection<String>? = null
): MessageItem =
    MessageItem(
        id = id,
        text = text,
        timestamp = timestamp,
        isMine = senderId == currentUserId,
        status = displayStatus(currentMembers),
        senderName = senderName
    )
