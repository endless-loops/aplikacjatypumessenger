package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.MessageRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
import com.example.aplikacjatypumessenger.services.ActiveChatTracker
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.appContainer
import com.example.aplikacjatypumessenger.utils.runSuspendCatching
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan ekranu rozmowy prywatnej: nazwa rozmówcy w pasku i lista wiadomości. */
data class ChatUiState(
    val title: String? = null,
    val messages: List<MessageItem> = emptyList()
)

sealed interface ChatEvent {
    data class ShowMessage(val text: UiText) : ChatEvent
}

/**
 * ViewModel rozmowy prywatnej (1:1). Id czatu i rozmówcy przychodzą z Intentu przez
 * SavedStateHandle, więc przetrwają też odtworzenie procesu przez system.
 */
class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val activeChatTracker: ActiveChatTracker
) : ViewModel() {

    private val chatId: String = savedStateHandle[Extras.CHAT_ID] ?: ""
    private val otherUserId: String = savedStateHandle[Extras.OTHER_USER_ID] ?: ""
    private val currentUserId = authRepository.currentUserId.orEmpty()

    // Czy ekran jest teraz widoczny - od tego zależy oznaczanie wiadomości jako przeczytanych
    private val isVisible = MutableStateFlow(false)

    private val _events = Channel<ChatEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val messages: StateFlow<List<Message>> = messageRepository.observeMessages(chatId)
        .catch { e ->
            _events.send(ChatEvent.ShowMessage(UiText.error(R.string.error_loading_messages, e)))
            emit(emptyList())
        }
        // Eagerly, bo z tej listy korzysta też oznaczanie przeczytanych, niezależnie od UI
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val otherUser = userRepository.observeUser(otherUserId)
        .catch { emit(null) }

    // WhileSubscribed(5 s): przy obrocie ekranu nasłuch nie jest zrywany,
    // a po wyjściu z ekranu zatrzymuje się po 5 sekundach
    val uiState: StateFlow<ChatUiState> =
        combine(messages, otherUser) { messages, user: User? ->
            ChatUiState(
                title = user?.username,
                messages = messages.map { it.toItem(currentUserId) }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launchReadReceipts(messages, isVisible, currentUserId, messageRepository)
    }

    /**
     * Wołane z onResume/onPause ekranu. Ustawia też aktywny czat, żeby [com.example.aplikacjatypumessenger.services.MessagingService]
     * nie pokazywał powiadomień o wiadomościach, które użytkownik właśnie widzi.
     */
    fun onScreenVisibilityChanged(visible: Boolean) {
        isVisible.value = visible
        if (visible) {
            activeChatTracker.activeChatId = chatId
        } else if (activeChatTracker.activeChatId == chatId) {
            // Czyścimy tylko własny czat - mógł już zostać otwarty inny
            activeChatTracker.activeChatId = null
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // Wiadomość pojawi się na liście od razu (zapis lokalny Firestore),
            // a ta korutyna czeka tylko na potwierdzenie z serwera
            runSuspendCatching {
                messageRepository.send(chatId, currentUserId, trimmed, receiverId = otherUserId)
            }.onFailure {
                _events.send(ChatEvent.ShowMessage(UiText.error(R.string.error_sending_message, it)))
            }
        }
    }

    override fun onCleared() {
        if (activeChatTracker.activeChatId == chatId) activeChatTracker.activeChatId = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    createSavedStateHandle(),
                    appContainer.authRepository,
                    appContainer.userRepository,
                    appContainer.messageRepository,
                    appContainer.activeChatTracker
                )
            }
        }
    }
}
