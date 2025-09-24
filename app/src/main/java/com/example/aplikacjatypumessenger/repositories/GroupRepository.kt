// app/src/main/java/com/example/aplikacjatypumessenger/repositories/GroupRepository.kt

package com.example.aplikacjatypumessenger.repositories

import com.example.aplikacjatypumessenger.models.Group
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class GroupRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private var groupsListener: ListenerRegistration? = null

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    // Pobierz grupy użytkownika w czasie rzeczywistym
    fun startListeningForUserGroups() {
        val currentUserId = auth.currentUser?.uid ?: return

        groupsListener = db.collection("chats")
            .whereEqualTo("group", true)
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val tempGroups = mutableListOf<Group>()
                snapshot?.documents?.forEach { doc ->
                    val groupName = doc.getString("groupName") ?: ""
                    val participants = doc.get("participants") as? List<String> ?: emptyList()
                    val adminId = doc.getString("groupAdmin") ?: ""
                    val createdAt = doc.getLong("createdAt") ?: 0L

                    val group = Group(
                        id = doc.id,
                        name = groupName,
                        adminId = adminId,
                        participantIds = participants,
                        createdAt = createdAt
                    )
                    tempGroups.add(group)
                }
                _groups.value = tempGroups
            }
    }

    // Utwórz nową grupę
    suspend fun createGroup(name: String, participantIds: List<String>): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val groupId = db.collection("chats").document().id

            val allParticipants = (participantIds + currentUserId).distinct()

            val groupData = hashMapOf(
                "id" to groupId,
                "groupName" to name,
                "group" to true,
                "groupAdmin" to currentUserId,
                "participants" to allParticipants,
                "createdAt" to System.currentTimeMillis(),
                "lastMessage" to mapOf<String, Any>(),
                "lastMessageTime" to 0L
            )

            db.collection("chats").document(groupId).set(groupData).await()
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Pobierz szczegóły grupy
    suspend fun getGroupDetails(groupId: String): Group? {
        return try {
            val doc = db.collection("chats").document(groupId).get().await()
            val groupName = doc.getString("groupName") ?: ""
            val participants = doc.get("participants") as? List<String> ?: emptyList()
            val adminId = doc.getString("groupAdmin") ?: ""
            val createdAt = doc.getLong("createdAt") ?: 0L

            Group(
                id = doc.id,
                name = groupName,
                adminId = adminId,
                participantIds = participants,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            null
        }
    }

    // Dodaj użytkownika do grupy
    suspend fun addUserToGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            db.collection("chats").document(groupId)
                .update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // Usuń użytkownika z grupy
    suspend fun removeUserFromGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            db.collection("chats").document(groupId)
                .update("participants", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopListening() {
        groupsListener?.remove()
        groupsListener = null
    }
}