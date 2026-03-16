package com.example.aplikacjatypumessenger.repositories

import android.util.Log
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

    /**
     * Nasłuchuje grup użytkownika w czasie rzeczywistym.
     * Grupy są przechowywane w kolekcji "chats" z polem isGroup=true.
     * UWAGA: Firestore nie obsługuje jednocześnie whereEqualTo + whereArrayContains na różnych polach
     * bez composite index, więc filtrujemy isGroup po stronie klienta.
     */
    fun startListeningForUserGroups() {
        val currentUserId = auth.currentUser?.uid ?: return

        groupsListener = db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GroupRepository", "Error listening to groups", error)
                    return@addSnapshotListener
                }

                val tempGroups = mutableListOf<Group>()
                snapshot?.documents?.forEach { doc ->
                    // Sprawdzaj zarówno "isGroup" jak i starsze pole "group" dla kompatybilności
                    val isGroup = doc.getBoolean("isGroup")
                        ?: doc.getBoolean("group")
                        ?: false
                    if (!isGroup) return@forEach

                    val group = Group(
                        id = doc.id,
                        name = doc.getString("groupName") ?: "",
                        adminId = doc.getString("groupAdmin") ?: "",
                        participantIds = (doc.get("participants") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList(),
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        description = doc.getString("description") ?: "",
                        groupImage = doc.getString("groupImage") ?: ""
                    )
                    tempGroups.add(group)
                }
                _groups.value = tempGroups
            }
    }

    suspend fun createGroup(name: String, participantIds: List<String>): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val groupId = db.collection("chats").document().id
            val allParticipants = (participantIds + currentUserId).distinct()

            val groupData = hashMapOf(
                "id" to groupId,
                "groupName" to name,
                "isGroup" to true,          // Używamy isGroup (spójne z modelem Chat)
                "group" to true,            // Zachowujemy też stare pole dla kompatybilności
                "groupAdmin" to currentUserId,
                "participants" to allParticipants,
                "createdAt" to System.currentTimeMillis(),
                "lastMessage" to mapOf<String, Any>(),
                "lastMessageTime" to 0L,
                "description" to "",
                "groupImage" to ""
            )

            db.collection("chats").document(groupId).set(groupData).await()
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupDetails(groupId: String): Group? {
        return try {
            val doc = db.collection("chats").document(groupId).get().await()
            Group(
                id = doc.id,
                name = doc.getString("groupName") ?: "",
                adminId = doc.getString("groupAdmin") ?: "",
                participantIds = (doc.get("participants") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList(),
                createdAt = doc.getLong("createdAt") ?: 0L,
                description = doc.getString("description") ?: "",
                groupImage = doc.getString("groupImage") ?: ""
            )
        } catch (e: Exception) {
            Log.w("GroupRepository", "getGroupDetails failed", e)
            null
        }
    }

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

    suspend fun removeUserFromGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            // Sprawdź czy użytkownik jest adminem przed usunięciem
            val group = getGroupDetails(groupId)
            if (group?.adminId == userId) {
                return Result.failure(Exception("Nie można usunąć administratora grupy"))
            }
            db.collection("chats").document(groupId)
                .update("participants", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(groupId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val group = getGroupDetails(groupId) ?: throw Exception("Grupa nie istnieje")
            if (group.adminId == currentUserId) {
                return Result.failure(Exception("Administrator nie może opuścić grupy. Najpierw przekaż rolę admina innemu członkowi."))
            }
            db.collection("chats").document(groupId)
                .update("participants", com.google.firebase.firestore.FieldValue.arrayRemove(currentUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMemberUsers(participantIds: List<String>): List<com.example.aplikacjatypumessenger.models.User> {
        return try {
            participantIds.mapNotNull { userId ->
                val doc = db.collection("users").document(userId).get().await()
                if (doc.exists()) {
                    com.example.aplikacjatypumessenger.models.User(
                        id = doc.id,
                        username = doc.getString("username") ?: "",
                        email = doc.getString("email") ?: "",
                        status = doc.getString("status") ?: "offline",
                        lastSeen = doc.getLong("lastSeen") ?: 0L
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.w("GroupRepository", "getMemberUsers failed", e)
            emptyList()
        }
    }

    fun stopListening() {
        groupsListener?.remove()
        groupsListener = null
    }
}
