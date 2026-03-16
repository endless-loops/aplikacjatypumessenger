package com.example.aplikacjatypumessenger.models

data class Chat(
    val id: String = "",
    val participants: List<String> = listOf(),
    val isGroup: Boolean = false,
    val groupName: String = "",
    val groupAdmin: String = "",
    val lastMessage: Map<String, Any>? = null,  // Przechowujemy jako Map żeby uniknąć problemów z deserializacją
    val lastMessageTime: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Nazwa czatu 1:1 — obliczana w runtime, nie przechowywana w Firestore
    // Używamy osobnego pola poza data class żeby nie psuć equals()/hashCode() DiffUtil
    @Transient
    var chatName: String = ""

    // Tekst ostatniej wiadomości (wyciągamy z lastMessage Map dla wygody)
    val lastMessageText: String
        get() = (lastMessage?.get("text") as? String) ?: ""
}
