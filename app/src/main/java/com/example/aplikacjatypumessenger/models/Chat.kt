package com.example.aplikacjatypumessenger.models

data class Chat(
    val id: String = "",
    val participants: List<String> = listOf(),
    val name: String = "",
    val lastMessage: Message? = null,
    @Transient var chatName: String = "", // nazwa drugiego użytkownika
    val lastMessageTime: Long = 0,
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)