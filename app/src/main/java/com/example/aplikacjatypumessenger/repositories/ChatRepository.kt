package com.example.aplikacjatypumessenger.repositories

import com.example.aplikacjatypumessenger.models.Chat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(private val firestore: FirebaseFirestore) {

    private val chats get() = firestore.collection(CHATS)

    fun observeChatsForUser(userId: String): Flow<List<Chat>> =
        chats.whereArrayContains(FIELD_PARTICIPANTS, userId).snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toChat() } }

    /** Emituje `null`, gdy czat został usunięty. */
    fun observeChat(chatId: String): Flow<Chat?> =
        chats.document(chatId).snapshots().map { it.toChat() }

    suspend fun findOrCreatePrivateChat(currentUserId: String, otherUserId: String): Chat {
        val existing = chats.whereArrayContains(FIELD_PARTICIPANTS, currentUserId).get().await()
            .documents.mapNotNull { it.toChat() }
            .firstOrNull { !it.isGroup && otherUserId in it.participants }
        if (existing != null) return existing

        val ref = chats.document()
        val chat = Chat(
            id = ref.id,
            participants = listOf(currentUserId, otherUserId),
            isGroup = false,
            createdAt = System.currentTimeMillis()
        )
        ref.set(chat).await()
        return chat
    }

    suspend fun createGroup(name: String, adminId: String, memberIds: Collection<String>): String {
        val ref = chats.document()
        val chat = Chat(
            id = ref.id,
            participants = (memberIds + adminId).distinct(),
            isGroup = true,
            groupName = name,
            groupAdmin = adminId,
            createdAt = System.currentTimeMillis()
        )
        ref.set(chat).await()
        return ref.id
    }

    suspend fun addMembers(chatId: String, userIds: Collection<String>) {
        chats.document(chatId)
            .update(FIELD_PARTICIPANTS, FieldValue.arrayUnion(*userIds.toTypedArray()))
            .await()
    }

    suspend fun removeMember(chatId: String, userId: String) {
        chats.document(chatId).update(FIELD_PARTICIPANTS, FieldValue.arrayRemove(userId)).await()
    }

    suspend fun setAdmin(chatId: String, userId: String) {
        chats.document(chatId).update(FIELD_GROUP_ADMIN, userId).await()
    }

    /** Admin opuszczający grupę przekazuje uprawnienia; ostatni członek usuwa grupę. */
    suspend fun leaveGroup(chat: Chat, userId: String) {
        val remaining = chat.participants - userId
        if (remaining.isEmpty()) {
            deleteGroup(chat.id)
            return
        }
        val updates = mutableMapOf<String, Any>(FIELD_PARTICIPANTS to FieldValue.arrayRemove(userId))
        if (chat.groupAdmin == userId) updates[FIELD_GROUP_ADMIN] = remaining.first()
        chats.document(chat.id).update(updates).await()
    }

    /** Wiadomości usuwa Cloud Function `deleteChatMessages`. */
    suspend fun deleteGroup(chatId: String) {
        chats.document(chatId).delete().await()
    }

    private fun DocumentSnapshot.toChat(): Chat? = toObject(Chat::class.java)?.copy(id = id)

    companion object {
        const val CHATS = "chats"
        const val FIELD_PARTICIPANTS = "participants"
        const val FIELD_GROUP_ADMIN = "groupAdmin"
        const val FIELD_LAST_MESSAGE = "lastMessage"
        const val FIELD_LAST_MESSAGE_TIME = "lastMessageTime"
    }
}
