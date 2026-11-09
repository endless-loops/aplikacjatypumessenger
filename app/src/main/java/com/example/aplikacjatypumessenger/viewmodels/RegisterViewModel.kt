package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.models.UserStatus
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.Validators
import com.example.aplikacjatypumessenger.utils.appContainer
import com.example.aplikacjatypumessenger.utils.runSuspendCatching
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val isLoading: Boolean = false,
    val usernameError: UiText? = null,
    val emailError: UiText? = null,
    val passwordError: UiText? = null
)

sealed interface RegisterEvent {
    data object NavigateToMain : RegisterEvent
    data class ShowMessage(val text: UiText) : RegisterEvent
}

/**
 * Rejestracja w dwóch krokach: konto w Firebase Auth, a potem profil w kolekcji `users`.
 * Stan i zdarzenia działają tak samo jak w [LoginViewModel].
 */
class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun register(email: String, password: String, username: String) {
        val trimmedEmail = email.trim()
        val trimmedUsername = username.trim()
        // Sprawdzamy wszystkie pola naraz, żeby użytkownik od razu zobaczył wszystkie błędy
        val state = RegisterUiState(
            usernameError = if (Validators.isValidUsername(trimmedUsername)) null
            else UiText.Res(R.string.error_username_too_short, listOf(Validators.MIN_USERNAME_LENGTH)),
            emailError = if (Validators.isValidEmail(trimmedEmail)) null else UiText.Res(R.string.error_invalid_email),
            passwordError = if (Validators.isValidPassword(password)) null
            else UiText.Res(R.string.error_password_too_short, listOf(Validators.MIN_PASSWORD_LENGTH))
        )
        _uiState.value = state
        if (state.usernameError != null || state.emailError != null || state.passwordError != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val userId = authRepository.register(trimmedEmail, password)
                try {
                    userRepository.createUser(
                        User(
                            id = userId,
                            email = trimmedEmail,
                            username = trimmedUsername,
                            status = UserStatus.ONLINE,
                            lastSeen = System.currentTimeMillis(),
                            // Token od razu przy rejestracji, żeby powiadomienia działały od pierwszej wiadomości
                            fcmToken = userRepository.fetchFcmToken()
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Bez profilu w Firestore konto byłoby bezużyteczne - wycofujemy rejestrację
                    runSuspendCatching { authRepository.deleteCurrentUser() }
                    _events.send(RegisterEvent.ShowMessage(UiText.error(R.string.error_saving_user, e)))
                    return@launch
                }
                _events.send(RegisterEvent.NavigateToMain)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(RegisterEvent.ShowMessage(UiText.error(R.string.error_register_failed, e)))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RegisterViewModel(appContainer.authRepository, appContainer.userRepository) }
        }
    }
}
