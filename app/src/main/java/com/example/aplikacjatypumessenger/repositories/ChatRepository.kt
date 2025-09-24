// app/src/main/java/com/example/aplikacjatypumessenger/repositories/ChatRepository.kt

package com.example.aplikacjatypumessenger.repositories

import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private var chatsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    // Ładuje czaty i użytkowników w czasie rzeczywistym
    fun startListeningForChatsAndUsers() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Słuchacz czatów
        chatsListener = db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val tempChats = mutableListOf<Chat>()
                snapshot?.documents?.forEach { doc ->
                    val chat = doc.toObject(Chat::class.java) ?: return@forEach
                    tempChats.add(chat)
                }
                _chats.value = tempChats
            }

        // Słuchacz użytkowników
        usersListener = db.collection("users")
            .whereNotEqualTo("id", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val tempUsers = snapshot?.documents?.mapNotNull {
                    it.toObject(User::class.java)
                } ?: emptyList()
                _users.value = tempUsers.distinctBy { it.id }
            }
    }

    // Tworzy prywatny czat
    suspend fun createPrivateChat(otherUserId: String): String {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
        val chatId = db.collection("chats").document().id

        val chat = Chat(
            id = chatId,
            participants = listOf(currentUserId, otherUserId),
            isGroup = false
        )

        db.collection("chats").document(chatId).set(chat).await()
        return chatId
    }

    // Zwraca nazwę czatu (dla czatów 1:1)
    suspend fun getChatName(chat: Chat): String {
        val currentUserId = auth.currentUser?.uid ?: return "Czat"
        if (chat.isGroup) return chat.groupName

        val otherUserId = chat.participants.first { it != currentUserId }
        val userDoc = db.collection("users").document(otherUserId).get().await()
        return userDoc.toObject(User::class.java)?.username ?: "Użytkownik"
    }

    fun stopListening() {
        chatsListener?.remove()
        usersListener?.remove()
    }
}