package com.example.aplikacjatypumessenger.services

/** Czat aktualnie widoczny na ekranie - dla niego nie pokazujemy powiadomień push. */
class ActiveChatTracker {
    @Volatile
    var activeChatId: String? = null
}
