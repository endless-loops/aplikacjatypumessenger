package com.example.aplikacjatypumessenger.models

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
    val status: String = "sending",     // "sending" | "sent" | "delivered" | "read"
    val deliveredAt: Long = 0L,
    val readAt: Long = 0L,
    val isGroupMessage: Boolean = false
)
