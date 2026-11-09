package com.example.aplikacjatypumessenger.models

import com.google.firebase.firestore.PropertyName

/**
 * Dokument z kolekcji `chats` - rozmowa prywatna (dwóch uczestników) albo grupa.
 * Wartości domyślne są potrzebne, bo Firestore tworzy obiekt bezargumentowym konstruktorem.
 * Ostatnia wiadomość jest trzymana w dokumencie czatu, żeby lista rozmów nie musiała
 * osobno pobierać wiadomości.
 */
data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: Message? = null,
    // Bez adnotacji Firestore zapisuje pole Kotlinowe `isGroup` jako "group" i nie potrafi go odczytać
    @get:PropertyName("isGroup") @set:PropertyName("isGroup")
    var isGroup: Boolean = false,
    val groupName: String = "",
    val groupAdmin: String = "",
    val lastMessageTime: Long = 0L,
    val createdAt: Long = 0L
)
