package com.example.aplikacjatypumessenger.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class Chat(
    val id: String = "",
    val participants: List<String> = listOf(),
    val name: String = "",
    val lastMessage: Message? = null,
    // Bez adnotacji Firestore zapisuje pole Kotlinowe `isGroup` jako "group" i nie potrafi go odczytać
    @get:PropertyName("isGroup") @set:PropertyName("isGroup")
    var isGroup: Boolean = false,
    val groupName: String = "",
    val groupAdmin: String = "",
    @get:Exclude var chatName: String = "", // nazwa drugiego użytkownika
    val lastMessageTime: Long = 0,
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
