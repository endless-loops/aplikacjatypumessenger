package com.example.aplikacjatypumessenger.repositories


import com.example.aplikacjatypumessenger.models.User  // ← DODAJ TEN IMPORT
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore  // ← DODAJ TEN IMPORT
import com.google.firebase.ktx.Firebase  // ← DODAJ TEN IMPORT
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class UserRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    suspend fun loginUser(email: String, password: String): Result<Boolean> {
        return try {
            // Timeout 10 sekund z Coroutines (lepsze niż Handler)
            withTimeout(10000L) {
                auth.signInWithEmailAndPassword(email, password).await()
                Result.success(true)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(TimeoutException("Logowanie zajmuje zbyt długo. Sprawdź połączenie z internetem."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerUser(email: String, password: String, username: String): Result<Boolean> {
        return try {
            // 1. Rejestracja w Firebase Auth
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("Failed to get user ID")

            // 2. Pobierz FCM token
            val fcmToken = FirebaseMessaging.getInstance().token.await()

            // 3. Zapisz użytkownika w Firestore
            val user = User(
                id = userId,
                email = email,
                username = username,
                profileImage = "",
                status = "online",
                lastSeen = System.currentTimeMillis(),
                fcmToken = fcmToken
            )

            Firebase.firestore.collection("users").document(userId).set(user).await()

            Result.success(true)
        } catch (e: Exception) {
            // Jeśli zapis w Firestore się nie uda, usuń użytkownika z Auth
            try {
                auth.currentUser?.delete()?.await()
            } catch (deleteError: Exception) {
                // Ignore delete error
            }
            Result.failure(e)
        }
    }
    // W UserRepository.kt DODAJ:

    suspend fun resetPassword(newPassword: String): Result<Boolean> {
        return try {
            val user = auth.currentUser
            if (user != null) {
                user.updatePassword(newPassword).await()
                Result.success(true)
            } else {
                Result.failure(Exception("Użytkownik nie jest zalogowany"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}