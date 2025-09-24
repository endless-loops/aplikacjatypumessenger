// app/src/main/java/com/example/aplikacjatypumessenger/repositories/MessageRepository.kt

package com.example.aplikacjatypumessenger.repositories

import android.util.Log
import com.example.aplikacjatypumessenger.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
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

    // Słuchacz wiadomości w czasie rzeczywistym
    fun startListeningForMessages(chatId: String) {
        stopListening() // Zatrzymaj poprzedni listener

        messageListener = db.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val tempMessages = mutableListOf<Message>()
                snapshot?.documents?.forEach { doc ->
                    val message = doc.toObject(Message::class.java) ?: return@forEach
                    tempMessages.add(message)
                }
                _messages.value = tempMessages
            }
    }

    fun startListeningForGroupMessages(groupId: String) {
        stopListening()

        messageListener = db.collection("messages")
            .whereEqualTo("chatId", groupId)
            .whereEqualTo("isGroupMessage", true)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MessageRepository", "Error listening to group messages", error)
                    return@addSnapshotListener
                }

                val tempMessages = mutableListOf<Message>()
                snapshot?.documents?.forEach { doc ->
                    val message = doc.toObject(Message::class.java) ?: return@forEach
                    tempMessages.add(message)
                }
                _messages.value = tempMessages
            }
    }

    // Wysyłanie wiadomości
    suspend fun sendMessage(
        chatId: String,
        otherUserId: String,
        text: String
    ): Result<String> {
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
                status = "sent", // Od razu "sent" zamiast "sending"
                seen = false
            )

            // Zapisz wiadomość w Firestore
            db.collection("messages").document(messageId).set(message).await()

            // Zaktualizuj ostatnią wiadomość w czacie
            updateChatLastMessage(chatId, message)

            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateChatLastMessage(chatId: String, message: Message) {
        val lastMessageData = hashMapOf(
            "text" to message.text,
            "senderId" to message.senderId,
            "timestamp" to message.timestamp,
            "seen" to message.seen
        )

        db.collection("chats").document(chatId)
            .update("lastMessage", lastMessageData)
            .await()
    }

    // Oznacz wiadomości jako przeczytane
    suspend fun markMessagesAsRead(chatId: String, userId: String) {
        try {
            val messages = db.collection("messages")
                .whereEqualTo("chatId", chatId)
                .whereEqualTo("senderId", userId)
                .whereEqualTo("seen", false)
                .get()
                .await()

            messages.documents.forEach { doc ->
                db.collection("messages").document(doc.id)
                    .update("status", "read", "seen", true)
                    .await()
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    fun stopListening() {
        messageListener?.remove()
        messageListener = null
    }

    suspend fun sendGroupMessage(
        groupId: String,
        text: String
    ): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val messageId = db.collection("messages").document().id
            val timestamp = System.currentTimeMillis()

            val message = Message(
                id = messageId,
                senderId = currentUserId,
                chatId = groupId,
                text = text,
                timestamp = timestamp,
                type = "text",
                status = "sent",
                seen = false,
                isGroupMessage = true
            )

            db.collection("messages").document(messageId).set(message).await()
            updateGroupLastMessage(groupId, message)

            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateGroupLastMessage(groupId: String, message: Message) {
        val lastMessageData = hashMapOf(
            "text" to message.text,
            "senderId" to message.senderId,
            "timestamp" to message.timestamp,
            "seen" to false
        )

        // ✅ POPRAWNA SKŁADNIA Firestore update:
        db.collection("chats").document(groupId)
            .update(
                mapOf(
                    "lastMessage" to lastMessageData,
                    "lastMessageTime" to message.timestamp
                )
            )
            .await()
    }

    suspend fun isGroupChat(chatId: String): Boolean {
        return try {
            val chatDoc = db.collection("chats").document(chatId).get().await()
            chatDoc.getBoolean("group") ?: false
        } catch (e: Exception) {
            false
        }
    }
}