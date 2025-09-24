package com.example.aplikacjatypumessenger.models

data class Group(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val adminId: String = "",
    val participantIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val groupImage: String = "",
    val lastMessage: Map<String, Any> = emptyMap()
) {
    constructor() : this(
        id = "",
        name = "",
        description = "",
        adminId = "",
        participantIds = emptyList(),
        createdAt = 0L,
        groupImage = "",
        lastMessage = emptyMap()
    )
}