package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.Validators
import com.example.aplikacjatypumessenger.utils.appContainer
import com.example.aplikacjatypumessenger.utils.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class LoginUiState(
    val isLoading: Boolean = false,
    val emailError: UiText? = null,
    val passwordError: UiText? = null
)

sealed interface LoginEvent {
    data object NavigateToMain : LoginEvent
    data class ShowMessage(val text: UiText) : LoginEvent
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun login(email: String, password: String) {
        val trimmedEmail = email.trim()
        val emailError = if (Validators.isValidEmail(trimmedEmail)) null else UiText.Res(R.string.error_invalid_email)
        val passwordError = if (Validators.isValidPassword(password)) null
        else UiText.Res(R.string.error_password_too_short, listOf(Validators.MIN_PASSWORD_LENGTH))
        _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
        if (emailError != null || passwordError != null || _uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val userId = withTimeout(LOGIN_TIMEOUT_MS) { authRepository.login(trimmedEmail, password) }
                // Ekran logowania zaraz się zamknie, więc zapis statusu/tokenu idzie w zakresie aplikacji
                applicationScope.launch { runSuspendCatching { userRepository.onSignedIn(userId) } }
                _events.send(LoginEvent.NavigateToMain)
            } catch (e: TimeoutCancellationException) {
                _events.send(LoginEvent.ShowMessage(UiText.Res(R.string.error_login_timeout)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(LoginEvent.ShowMessage(UiText.error(R.string.error_login_failed, e)))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val trimmedEmail = email.trim()
        if (!Validators.isValidEmail(trimmedEmail)) {
            _events.trySend(LoginEvent.ShowMessage(UiText.Res(R.string.error_invalid_email)))
            return
        }
        viewModelScope.launch {
            val message = try {
                authRepository.sendPasswordReset(trimmedEmail)
                UiText.Res(R.string.password_reset_sent, listOf(trimmedEmail))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiText.error(R.string.error_generic, e)
            }
            _events.send(LoginEvent.ShowMessage(message))
        }
    }

    companion object {
        private const val LOGIN_TIMEOUT_MS = 10_000L

        val Factory = viewModelFactory {
            initializer {
                LoginViewModel(appContainer.authRepository, appContainer.userRepository, appContainer.applicationScope)
            }
        }
    }
}
