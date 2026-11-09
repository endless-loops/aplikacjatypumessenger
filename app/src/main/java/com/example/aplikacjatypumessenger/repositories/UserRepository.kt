package com.example.aplikacjatypumessenger.repositories

import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.models.UserStatus
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Profile użytkowników w kolekcji `users`: lista użytkowników, status online
 * i token FCM potrzebny do powiadomień.
 */
class UserRepository(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {

    private val users get() = firestore.collection(USERS)

    /** Wszyscy użytkownicy - do listy na ekranie głównym i wyboru członków grupy. */
    fun observeUsers(): Flow<List<User>> =
        users.snapshots().map { snapshot -> snapshot.toObjects(User::class.java) }

    fun observeUser(userId: String): Flow<User?> =
        users.document(userId).snapshots().map { it.toObject(User::class.java) }

    /** Pobiera użytkowników po id w paczkach, bo zapytanie `in` ma limit wartości. */
    suspend fun getUsers(userIds: Collection<String>): List<User> =
        userIds.distinct().filter { it.isNotEmpty() }.chunked(IN_QUERY_LIMIT).flatMap { chunk ->
            users.whereIn(FieldPath.documentId(), chunk).get().await().toObjects(User::class.java)
        }

    suspend fun createUser(user: User) {
        users.document(user.id).set(user).await()
    }

    /** Brak tokenu (np. urządzenie bez usług Google) nie powinien blokować logowania, stąd pusty napis. */
    suspend fun fetchFcmToken(): String =
        runCatching { messaging.token.await() }.getOrDefault("")

    suspend fun updateFcmToken(userId: String, token: String) {
        users.document(userId).update(FIELD_FCM_TOKEN, token).await()
    }

    /** Wywoływane przez [com.example.aplikacjatypumessenger.services.PresenceObserver]. */
    suspend fun setOnline(userId: String, online: Boolean) {
        users.document(userId).update(
            FIELD_STATUS, if (online) UserStatus.ONLINE else UserStatus.OFFLINE,
            FIELD_LAST_SEEN, System.currentTimeMillis()
        ).await()
    }

    /** Po zalogowaniu: oznacz jako online i odśwież token FCM dla tego urządzenia. */
    suspend fun onSignedIn(userId: String) {
        setOnline(userId, true)
        val token = fetchFcmToken()
        if (token.isNotEmpty()) updateFcmToken(userId, token)
    }

    /** Przed wylogowaniem: urządzenie przestaje dostawać powiadomienia dla tego konta. */
    suspend fun onSigningOut(userId: String) {
        users.document(userId).update(
            FIELD_STATUS, UserStatus.OFFLINE,
            FIELD_LAST_SEEN, System.currentTimeMillis(),
            FIELD_FCM_TOKEN, ""
        ).await()
    }

    private companion object {
        const val USERS = "users"
        const val FIELD_STATUS = "status"
        const val FIELD_LAST_SEEN = "lastSeen"
        const val FIELD_FCM_TOKEN = "fcmToken"
        const val IN_QUERY_LIMIT = 10 // bezpieczna wartość, starsze wersje Firestore pozwalały na 10
    }
}
