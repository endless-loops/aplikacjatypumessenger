package com.example.aplikacjatypumessenger.models

data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val profileImage: String = "",
    val status: String = UserStatus.OFFLINE,
    val lastSeen: Long = 0L,
    val fcmToken: String = ""
) {
    val isOnline: Boolean get() = status == UserStatus.ONLINE
}

object UserStatus {
    const val ONLINE = "online"
    const val OFFLINE = "offline"
}
