package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.NotSignedInException
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.Validators
import com.example.aplikacjatypumessenger.utils.appContainer
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val isLoading: Boolean = false,
    val newPasswordError: UiText? = null,
    val confirmPasswordError: UiText? = null
)

sealed interface ResetPasswordEvent {
    data class PasswordChanged(val text: UiText) : ResetPasswordEvent
    data class ShowMessage(val text: UiText) : ResetPasswordEvent
}

/**
 * Zmiana hasła przez zalogowanego użytkownika (osobny ekran). Reset hasła dla osoby,
 * która go nie pamięta, jest w [LoginViewModel.sendPasswordReset].
 */
class ResetPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<ResetPasswordEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun changePassword(newPassword: String, confirmPassword: String) {
        val state = ResetPasswordUiState(
            newPasswordError = if (Validators.isValidPassword(newPassword)) null
            else UiText.Res(R.string.error_password_too_short, listOf(Validators.MIN_PASSWORD_LENGTH)),
            confirmPasswordError = if (newPassword == confirmPassword) null
            else UiText.Res(R.string.error_passwords_differ)
        )
        _uiState.value = state
        if (state.newPasswordError != null || state.confirmPasswordError != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.updatePassword(newPassword)
                _events.send(ResetPasswordEvent.PasswordChanged(UiText.Res(R.string.password_changed)))
            } catch (e: NotSignedInException) {
                // Sesja wygasła w międzyczasie - trzeba zalogować się ponownie
                _events.send(ResetPasswordEvent.ShowMessage(UiText.Res(R.string.error_relogin_required)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ResetPasswordEvent.ShowMessage(UiText.error(R.string.error_password_change, e)))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ResetPasswordViewModel(appContainer.authRepository) }
        }
    }
}
