package com.example.aplikacjatypumessenger.repositories

import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.models.MessageStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Wysyłanie wiadomości i obsługa ich statusów (wysłana / dostarczona / przeczytana).
 * Powiadomienia push nie są wysyłane stąd, tylko przez Cloud Function reagującą
 * na nowy dokument w kolekcji `messages`.
 */
class MessageRepository(private val firestore: FirebaseFirestore) {

    private val messages get() = firestore.collection(MESSAGES)

    /**
     * Wiadomości czatu w kolejności chronologicznej. Wiadomości jeszcze niezapisane na serwerze
     * dostają status [MessageStatus.SENDING], więc UI nie potrzebuje osobnej listy lokalnej.
     */
    fun observeMessages(chatId: String): Flow<List<Message>> =
        messages.whereEqualTo(FIELD_CHAT_ID, chatId)
            .orderBy(FIELD_TIMESTAMP, Query.Direction.ASCENDING)
            // INCLUDE - dostajemy też zdarzenie, gdy serwer potwierdzi zapis, a nie tylko zmianę danych
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val message = doc.toObject(Message::class.java)?.copy(id = doc.id)
                    if (message != null && doc.metadata.hasPendingWrites()) {
                        message.copy(status = MessageStatus.SENDING)
                    } else {
                        message
                    }
                }
            }

    /**
     * Zapisuje wiadomość i podgląd ostatniej wiadomości czatu w jednej operacji.
     * @param recipients w grupach członkowie, od których zależy status "przeczytana przez wszystkich"
     */
    suspend fun send(
        chatId: String,
        senderId: String,
        text: String,
        receiverId: String = "",
        isGroup: Boolean = false,
        recipients: List<String> = emptyList()
    ) {
        val messageRef = messages.document()
        // Czas z telefonu, a nie serverTimestamp(), bo wiadomość ma się od razu ustawić
        // na właściwym miejscu listy, także bez połączenia z siecią
        val timestamp = System.currentTimeMillis()
        val message = Message(
            id = messageRef.id,
            senderId = senderId,
            receiverId = receiverId,
            chatId = chatId,
            text = text,
            timestamp = timestamp,
            status = MessageStatus.SENT,
            isGroupMessage = isGroup,
            recipients = recipients
        )
        val lastMessage = mapOf(
            "text" to text,
            "senderId" to senderId,
            "timestamp" to timestamp
        )
        val chatRef = firestore.collection(ChatRepository.CHATS).document(chatId)

        // Batch: albo zapisze się i wiadomość, i podgląd na liście czatów, albo nic
        firestore.batch()
            .set(messageRef, message)
            .update(
                chatRef,
                ChatRepository.FIELD_LAST_MESSAGE, lastMessage,
                ChatRepository.FIELD_LAST_MESSAGE_TIME, timestamp
            )
            .commit()
            .await()
    }

    /**
     * Oznacza wiadomości jako przeczytane i dopisuje czytającego do `readBy`.
     * `status` zostaje ustawiony także w grupach, żeby starsze wersje aplikacji nadal coś pokazywały.
     */
    suspend fun markAsRead(messageIds: Collection<String>, readerId: String) {
        val now = System.currentTimeMillis()
        // Jeden batch może zawierać najwyżej 500 operacji
        messageIds.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id ->
                batch.update(
                    messages.document(id),
                    mapOf(
                        FIELD_STATUS to MessageStatus.READ,
                        FIELD_SEEN to true,
                        FIELD_READ_AT to now,
                        FIELD_READ_BY to FieldValue.arrayUnion(readerId)
                    )
                )
            }
            batch.commit().await()
        }
    }

    /** Oznacza wiadomość jako dostarczoną, nie cofając statusu "przeczytana". */
    suspend fun markAsDelivered(messageId: String) {
        val ref = messages.document(messageId)
        // Transakcja, bo powiadomienie może dojść już po przeczytaniu wiadomości
        // i zwykły update zmieniłby "przeczytana" z powrotem na "dostarczona"
        firestore.runTransaction { transaction ->
            if (transaction.get(ref).getString(FIELD_STATUS) == MessageStatus.SENT) {
                transaction.update(
                    ref,
                    mapOf(FIELD_STATUS to MessageStatus.DELIVERED, FIELD_DELIVERED_AT to System.currentTimeMillis())
                )
            }
        }.await()
    }

    private companion object {
        const val MESSAGES = "messages"
        const val FIELD_CHAT_ID = "chatId"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_STATUS = "status"
        const val FIELD_SEEN = "seen"
        const val FIELD_READ_AT = "readAt"
        const val FIELD_DELIVERED_AT = "deliveredAt"
        const val FIELD_READ_BY = "readBy"
        const val BATCH_LIMIT = 500
    }
}
