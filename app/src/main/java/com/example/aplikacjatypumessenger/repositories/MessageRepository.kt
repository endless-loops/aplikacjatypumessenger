package com.example.aplikacjatypumessenger.repositories

import android.util.Log
import com.example.aplikacjatypumessenger.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class MessageRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private var messageListener: ListenerRegistration? = null

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    fun startListeningForMessages(chatId: String) {
        stopListening()
        messageListener = db.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MessageRepository", "Error listening to messages", error)
                    return@addSnapshotListener
                }
                _messages.value = snapshot?.documents?.mapNotNull {
                    it.toObject(Message::class.java)
                } ?: emptyList()
            }
    }

    fun startListeningForGroupMessages(groupId: String) {
        stopListening()
        // Filtrujemy tylko po chatId — każdy czat ma unikalny ID,
        // więc nie potrzebujemy drugiego filtra (który wymagałby composite index w Firestore)
        messageListener = db.collection("messages")
            .whereEqualTo("chatId", groupId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MessageRepository", "Error listening to group messages", error)
                    return@addSnapshotListener
                }
                _messages.value = snapshot?.documents?.mapNotNull {
                    it.toObject(Message::class.java)
                } ?: emptyList()
            }
    }

    suspend fun sendMessage(chatId: String, otherUserId: String, text: String): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val messageId = db.collection("messages").document().id
            val timestamp = System.currentTimeMillis()

            val message = Message(
                id = messageId,
                senderId = currentUserId,
                receiverId = otherUserId,
                chatId = chatId,
                text = text,
                timestamp = timestamp,
                type = "text",
                status = "sent",
                seen = false,
                isGroupMessage = false
            )

            db.collection("messages").document(messageId).set(message).await()
            updateChatLastMessage(chatId, message)
            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(groupId: String, text: String): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val messageId = db.collection("messages").document().id
            val timestamp = System.currentTimeMillis()

            val message = Message(
                id = messageId,
                senderId = currentUserId,
                receiverId = "",
                chatId = groupId,
                text = text,
                timestamp = timestamp,
                type = "text",
                status = "sent",
                seen = false,
                isGroupMessage = true
            )

            db.collection("messages").document(messageId).set(message).await()
            updateChatLastMessage(groupId, message)
            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Oznacza wiadomości wysłane przez [senderId] w [chatId] jako przeczytane.
     * Ustawia status="read", seen=true oraz readAt=teraz.
     */
    suspend fun markMessagesAsRead(chatId: String, senderId: String) {
        try {
            val unread = db.collection("messages")
                .whereEqualTo("chatId", chatId)
                .whereEqualTo("senderId", senderId)
                .whereEqualTo("seen", false)
                .get()
                .await()

            val now = System.currentTimeMillis()
            unread.documents.forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "status" to "read",
                        "seen" to true,
                        "readAt" to now
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.w("MessageRepository", "markMessagesAsRead failed", e)
        }
    }

    /**
     * Oznacza wiadomości jako dostarczone (po połączeniu odbiorcy z chatId).
     */
    suspend fun markMessagesAsDelivered(chatId: String, senderId: String) {
        try {
            val undelivered = db.collection("messages")
                .whereEqualTo("chatId", chatId)
                .whereEqualTo("senderId", senderId)
                .whereEqualTo("status", "sent")
                .get()
                .await()

            val now = System.currentTimeMillis()
            undelivered.documents.forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "status" to "delivered",
                        "deliveredAt" to now
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.w("MessageRepository", "markMessagesAsDelivered failed", e)
        }
    }

    private suspend fun updateChatLastMessage(chatId: String, message: Message) {
        try {
            db.collection("chats").document(chatId)
                .update(
                    mapOf(
                        "lastMessage" to mapOf(
                            "text" to message.text,
                            "senderId" to message.senderId,
                            "timestamp" to message.timestamp,
                            "seen" to message.seen
                        ),
                        "lastMessageTime" to message.timestamp
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.w("MessageRepository", "updateChatLastMessage failed", e)
        }
    }

    fun stopListening() {
        messageListener?.remove()
        messageListener = null
        _messages.value = emptyList()
    }
}
