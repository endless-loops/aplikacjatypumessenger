package com.example.aplikacjatypumessenger.models

import com.google.firebase.firestore.PropertyName

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
