package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikacjatypumessenger.repositories.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val userRepository: UserRepository = UserRepository()) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun loginUser(email: String, password: String) {
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = userRepository.loginUser(email, password)

            _loginState.value = when {
                result.isSuccess -> LoginState.Success
                else -> LoginState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState

    fun registerUser(email: String, password: String, username: String) {
        _registerState.value = RegisterState.Loading

        viewModelScope.launch {
            val result = userRepository.registerUser(email, password, username)

            _registerState.value = when {
                result.isSuccess -> RegisterState.Success
                else -> RegisterState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    private val _resetPasswordState = MutableStateFlow<ResetPasswordState>(ResetPasswordState.Idle)
    val resetPasswordState: StateFlow<ResetPasswordState> = _resetPasswordState

    fun resetPassword(newPassword: String) {
        _resetPasswordState.value = ResetPasswordState.Loading

        viewModelScope.launch {
            val result = userRepository.resetPassword(newPassword)

            _resetPasswordState.value = when {
                result.isSuccess -> ResetPasswordState.Success
                else -> ResetPasswordState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
}

// stany resetowania hasła
sealed class ResetPasswordState {
    object Idle : ResetPasswordState()
    object Loading : ResetPasswordState()
    object Success : ResetPasswordState()
    data class Error(val message: String) : ResetPasswordState()
}
// stany logowanai
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
// stany rejestracji
sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    object Success : RegisterState()
    data class Error(val message: String) : RegisterState()
}