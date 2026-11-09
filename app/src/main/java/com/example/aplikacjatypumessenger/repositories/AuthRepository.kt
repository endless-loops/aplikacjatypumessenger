package com.example.aplikacjatypumessenger.repositories

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Warstwa nad Firebase Authentication. ViewModele nie korzystają z FirebaseAuth bezpośrednio,
 * tylko przez to repozytorium - dzięki temu logikę ekranów łatwiej podmienić i testować.
 * Funkcje `suspend` zamieniają `Task` z Firebase na korutyny przez `await()`.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    /** UID zalogowanego użytkownika albo `null`, gdy nikt nie jest zalogowany. */
    val currentUserId: String? get() = auth.currentUser?.uid

    val currentUserEmail: String? get() = auth.currentUser?.email

    /** Zwraca UID użytkownika; błędne dane kończą się wyjątkiem z Firebase. */
    suspend fun login(email: String, password: String): String =
        auth.signInWithEmailAndPassword(email, password).await().user?.uid
            ?: error("Brak użytkownika po zalogowaniu")

    suspend fun register(email: String, password: String): String =
        auth.createUserWithEmailAndPassword(email, password).await().user?.uid
            ?: error("Brak użytkownika po rejestracji")

    /**
     * Używane przy rejestracji, gdy konto w Auth już powstało, ale nie udał się zapis profilu
     * w Firestore - bez tego zostałoby konto bez profilu.
     */
    suspend fun deleteCurrentUser() {
        auth.currentUser?.delete()?.await()
    }

    /** Firebase sam wysyła e-mail z linkiem do ustawienia nowego hasła. */
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

/** Osobny typ wyjątku, żeby ViewModel mógł pokazać dla tego przypadku własny komunikat. */
class NotSignedInException : IllegalStateException("Użytkownik nie jest zalogowany")
