package com.example.aplikacjatypumessenger.utils

/**
 * Walidacja formularzy logowania i rejestracji po stronie klienta. Minimalna długość hasła
 * odpowiada wymaganiu Firebase Auth, więc błąd widać od razu, bez zapytania do serwera.
 */
object Validators {
    const val MIN_PASSWORD_LENGTH = 6
    const val MIN_USERNAME_LENGTH = 3

    // Bez android.util.Patterns, żeby walidację dało się testować zwykłymi testami JVM
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)

    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    fun isValidUsername(username: String): Boolean = username.trim().length >= MIN_USERNAME_LENGTH
}
