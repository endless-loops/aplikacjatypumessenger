package com.example.aplikacjatypumessenger.models

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val chatId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text", // text, image, video
    val mediaUrl: String = "",
    val seen: Boolean = false,
    var status: String = "sending", // "wysyłanie", "wysłane", "dostarczone", "przeczytane"
    val deliveredAt: Long = 0L,
    val readAt: Long = 0L,
    val isGroupMessage: Boolean = false
) {
    // No-arg constructor for Firestore
    constructor() : this(
        id = "",
        senderId = "",
        receiverId = "",
        chatId = "",
        text = "",
        timestamp = 0L,
        type = "text",
        mediaUrl = "",
        seen = false,
        status = "sending",
        deliveredAt = 0L,
        readAt = 0L,
        isGroupMessage = false
    )
}
