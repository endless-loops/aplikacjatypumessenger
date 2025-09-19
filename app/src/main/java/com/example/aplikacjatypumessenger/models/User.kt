package com.example.aplikacjatypumessenger.models

data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val profileImage: String = "",
    val status: String = "online",
    val lastSeen: Long = System.currentTimeMillis(),
    val fcmToken: String = ""
)