package com.example.aplikacjatypumessenger.models

import com.google.firebase.firestore.PropertyName

/**
 * Dokument z kolekcji `messages`. Wiadomości wszystkich czatów są w jednej kolekcji
 * i rozróżnia je pole [chatId] - prościej to zapytać i zabezpieczyć regułami niż podkolekcje.
 */
data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val chatId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val type: String = "text",
    val mediaUrl: String = "",
    val seen: Boolean = false,
    val status: String = MessageStatus.SENT,
    val deliveredAt: Long = 0L,
    val readAt: Long = 0L,
    /** Tylko w grupach: członkowie grupy (bez nadawcy) w chwili wysłania. Puste w starszych wiadomościach. */
    val recipients: List<String> = emptyList(),
    /** Kto już przeczytał wiadomość; każdy dopisuje tu wyłącznie siebie. */
    val readBy: List<String> = emptyList(),
    // Ta sama sztuczka co w Chat.isGroup - inaczej Firestore zapisze pole jako "groupMessage"
    @get:PropertyName("isGroupMessage") @set:PropertyName("isGroupMessage")
    var isGroupMessage: Boolean = false
)

/** Wartości pola `Message.status` zapisywane w Firestore. */
object MessageStatus {
    /** Tylko lokalnie: wiadomość czeka na zapis na serwerze. */
    const val SENDING = "sending"
    const val SENT = "sent"
    const val DELIVERED = "delivered"
    const val READ = "read"
}
