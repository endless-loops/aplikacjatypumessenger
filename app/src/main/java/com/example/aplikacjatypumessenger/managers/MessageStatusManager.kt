package com.example.aplikacjatypumessenger.managers

import android.util.Log
import com.google.firebase.firestore.*
import com.example.aplikacjatypumessenger.models.Message

class MessageStatusManager(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "MessageStatusManager"
        private const val MESSAGES_COLLECTION = "messages"
    }

    fun sendMessage(message: Message, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val messageRef = firestore.collection(MESSAGES_COLLECTION).document()
        val messageWithId = message.copy(id = messageRef.id, status = "sending")

        messageRef.set(messageWithId)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully: ${messageWithId.id}")
                updateMessageStatus(messageWithId.id, "sent")
                onSuccess(messageWithId.id)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to send message", exception)
                updateMessageStatus(messageWithId.id, "failed")
                onFailure(exception)
            }
    }

    fun updateMessageStatus(messageId: String, newStatus: String) {
        firestore.collection(MESSAGES_COLLECTION).document(messageId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Log.d(TAG, "Status updated to $newStatus for message: $messageId")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to update message status", exception)
            }
    }

    fun markMessagesAsDelivered(chatId: String, currentUserId: String) {
        firestore.collection(MESSAGES_COLLECTION)
            .whereEqualTo("chatId", chatId)
            .whereNotEqualTo("senderId", currentUserId)
            .whereEqualTo("status", "sent")
            .get()
            .addOnSuccessListener { documents ->
                val batch = firestore.batch()
                for (document in documents) {
                    batch.update(document.reference, "status", "delivered")
                }
                if (!documents.isEmpty) {
                    batch.commit().addOnSuccessListener {
                        Log.d(TAG, "Messages marked as delivered")
                    }
                }
            }
    }

    fun markMessagesAsRead(chatId: String, currentUserId: String) {
        firestore.collection(MESSAGES_COLLECTION)
            .whereEqualTo("chatId", chatId)
            .whereNotEqualTo("senderId", currentUserId)
            .whereIn("status", listOf("sent", "delivered"))
            .get()
            .addOnSuccessListener { documents ->
                val batch = firestore.batch()
                for (document in documents) {
                    batch.update(document.reference, mapOf(
                        "status" to "read",
                        "seen" to true
                    ))
                }
                if (!documents.isEmpty) {
                    batch.commit().addOnSuccessListener {
                        Log.d(TAG, "Messages marked as read")
                    }
                }
            }
    }

    fun listenForMessageUpdates(chatId: String, onMessageUpdate: (Message) -> Unit): ListenerRegistration {
        return firestore.collection(MESSAGES_COLLECTION)
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            val message = change.document.toObject(Message::class.java)
                            onMessageUpdate(message)
                        }
                        DocumentChange.Type.MODIFIED -> {
                            val message = change.document.toObject(Message::class.java)
                            onMessageUpdate(message)
                            Log.d(TAG, "Message updated: ${message.id} status: ${message.status}")
                        }
                        DocumentChange.Type.REMOVED -> {
                            TODO()
                        }
                    }
                }
            }
    }
}