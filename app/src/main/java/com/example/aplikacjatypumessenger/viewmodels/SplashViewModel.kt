package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.utils.appContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ekran startowy: po krótkiej chwili decyduje, czy przejść do ekranu głównego
 * (użytkownik zalogowany), czy do logowania. Firebase pamięta sesję między uruchomieniami,
 * więc wystarczy sprawdzić, czy jest bieżący użytkownik.
 */
class SplashViewModel(authRepository: AuthRepository) : ViewModel() {

    enum class Destination { MAIN, LOGIN }

    // null = jeszcze nie wiadomo, dokąd przejść
    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            delay(SPLASH_DURATION_MS)
            _destination.value =
                if (authRepository.currentUserId != null) Destination.MAIN else Destination.LOGIN
        }
    }

    companion object {
        private const val SPLASH_DURATION_MS = 2000L

        // Fabryka przekazuje repozytoria z AppContainer (ViewModel nie może mieć
        // parametrów w konstruktorze bez własnej fabryki). Ten sam wzorzec jest we wszystkich ViewModelach.
        val Factory = viewModelFactory {
            initializer { SplashViewModel(appContainer.authRepository) }
        }
    }
}
