package com.example.aplikacjatypumessenger.repositories

import android.util.Log
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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

    fun startListeningForChatsAndUsers() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Nasłuchuj czatów w których jest obecny użytkownik (1:1 i grupy)
        chatsListener = db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "Error listening to chats", error)
                    return@addSnapshotListener
                }
                // Ręczna deserializacja zamiast toObject() — Kotlin data class z val
                // nie ma setterów, więc Firestore reflection nie może ustawić pól.
                _chats.value = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Chat(
                            id = doc.id,
                            participants = (doc.get("participants") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            isGroup = doc.getBoolean("isGroup")
                                ?: doc.getBoolean("group") ?: false,
                            groupName = doc.getString("groupName") ?: "",
                            groupAdmin = doc.getString("groupAdmin") ?: "",
                            lastMessage = @Suppress("UNCHECKED_CAST")
                            (doc.get("lastMessage") as? Map<String, Any>),
                            lastMessageTime = doc.getLong("lastMessageTime") ?: 0L,
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    } catch (e: Exception) {
                        Log.w("ChatRepository", "Failed to parse chat ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
            }

        // Nasłuchuj wszystkich użytkowników — filtruj bieżącego po stronie klienta
        // (whereNotEqualTo wymaga composite index i nie filtruje po id dokumentu poprawnie)
        usersListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "Error listening to users", error)
                    return@addSnapshotListener
                }
                _users.value = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(User::class.java)
                }?.filter { it.id != currentUserId }   // Filtruj po stronie klienta
                    ?.distinctBy { it.id }
                    ?: emptyList()
            }
    }

    suspend fun createPrivateChat(otherUserId: String): String {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
        val chatId = db.collection("chats").document().id

        val chat = Chat(
            id = chatId,
            participants = listOf(currentUserId, otherUserId),
            isGroup = false,
            createdAt = System.currentTimeMillis()
        )

        db.collection("chats").document(chatId).set(chat).await()
        return chatId
    }

    suspend fun getChatName(chat: Chat): String {
        val currentUserId = auth.currentUser?.uid ?: return "Czat"
        if (chat.isGroup) return chat.groupName

        val otherUserId = chat.participants.firstOrNull { it != currentUserId } ?: return "Czat"
        return try {
            val userDoc = db.collection("users").document(otherUserId).get().await()
            userDoc.getString("username") ?: "Użytkownik"
        } catch (e: Exception) {
            "Użytkownik"
        }
    }

    fun stopListening() {
        chatsListener?.remove()
        chatsListener = null
        usersListener?.remove()
        usersListener = null
    }
}
