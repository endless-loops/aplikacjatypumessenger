package com.example.aplikacjatypumessenger.models

import com.google.firebase.firestore.PropertyName

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
    @get:PropertyName("isGroupMessage") @set:PropertyName("isGroupMessage")
    var isGroupMessage: Boolean = false
)
