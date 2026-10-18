package com.example.aplikacjatypumessenger.repositories

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth) {

    val currentUserId: String? get() = auth.currentUser?.uid

    val currentUserEmail: String? get() = auth.currentUser?.email

    suspend fun login(email: String, password: String): String =
        auth.signInWithEmailAndPassword(email, password).await().user?.uid
            ?: error("Brak użytkownika po zalogowaniu")

    suspend fun register(email: String, password: String): String =
        auth.createUserWithEmailAndPassword(email, password).await().user?.uid
            ?: error("Brak użytkownika po rejestracji")

    suspend fun deleteCurrentUser() {
        auth.currentUser?.delete()?.await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun updatePassword(newPassword: String) {
        val user = auth.currentUser ?: throw NotSignedInException()
        user.updatePassword(newPassword).await()
    }

    fun logout() {
        auth.signOut()
    }
}

class NotSignedInException : IllegalStateException("Użytkownik nie jest zalogowany")
