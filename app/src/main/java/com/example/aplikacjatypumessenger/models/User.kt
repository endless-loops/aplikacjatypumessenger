package com.example.aplikacjatypumessenger.models

/**
 * Profil użytkownika z kolekcji `users`. Identyfikator dokumentu to UID z Firebase Auth,
 * dzięki czemu reguły bezpieczeństwa łatwo sprawdzają, czy ktoś edytuje własny profil.
 */
data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val profileImage: String = "",
    val status: String = UserStatus.OFFLINE,
    val lastSeen: Long = 0L,
    /** Token urządzenia, na które Cloud Function wysyła powiadomienia push. */
    val fcmToken: String = ""
) {
    val isOnline: Boolean get() = status == UserStatus.ONLINE
}

/** Wartości pola `User.status`. */
object UserStatus {
    const val ONLINE = "online"
    const val OFFLINE = "offline"
}
