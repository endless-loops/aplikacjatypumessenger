package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.ChatRepository
import com.example.aplikacjatypumessenger.repositories.MessageRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
import com.example.aplikacjatypumessenger.services.ActiveChatTracker
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.appContainer
import com.example.aplikacjatypumessenger.utils.runSuspendCatching
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupChatUiState(
    val groupName: String? = null,
    val memberCount: Int = 0,
    val messages: List<MessageItem> = emptyList()
)

sealed interface GroupChatEvent {
    data class ShowMessage(val text: UiText) : GroupChatEvent
    data class Close(val text: UiText) : GroupChatEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class GroupChatViewModel(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val activeChatTracker: ActiveChatTracker
) : ViewModel() {

    private val chatId: String = savedStateHandle[Extras.CHAT_ID] ?: ""
    private val currentUserId = authRepository.currentUserId.orEmpty()

    private val _events = Channel<GroupChatEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** `null` dopóki dane się ładują; usunięcie z grupy zamyka ekran. */
    private val chat: StateFlow<Chat?> = chatRepository.observeChat(chatId)
        .onEach { chat ->
            if (chat == null || currentUserId !in chat.participants) {
                _events.send(GroupChatEvent.Close(UiText.Res(R.string.group_no_longer_member)))
            }
        }
        .catch { _events.send(GroupChatEvent.Close(UiText.Res(R.string.group_no_longer_member))) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val messages: StateFlow<List<Message>> = messageRepository.observeMessages(chatId)
        .catch { e ->
            _events.send(GroupChatEvent.ShowMessage(UiText.error(R.string.error_loading_messages, e)))
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Nazwy obecnych członków oraz autorów starszych wiadomości, którzy mogli już odejść. */
    private val usersById: Flow<Map<String, User>> =
        combine(chat.filterNotNull(), messages) { chat, messages ->
            chat.participants.toSet() + messages.map { it.senderId }
        }
            .distinctUntilChanged()
            .mapLatest { ids -> userRepository.getUsers(ids).associateBy { it.id } }
            .catch { emit(emptyMap()) }

    val uiState: StateFlow<GroupChatUiState> =
        combine(chat, messages, usersById) { chat, messages, users ->
            GroupChatUiState(
                groupName = chat?.groupName,
                memberCount = chat?.participants?.size ?: 0,
                messages = messages.map { message ->
                    message.toItem(currentUserId, senderName = users[message.senderId]?.username)
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChatUiState())

    fun onScreenVisibilityChanged(visible: Boolean) {
        if (visible) {
            activeChatTracker.activeChatId = chatId
        } else if (activeChatTracker.activeChatId == chatId) {
            activeChatTracker.activeChatId = null
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runSuspendCatching {
                messageRepository.send(chatId, currentUserId, trimmed, isGroup = true)
            }.onFailure {
                _events.send(GroupChatEvent.ShowMessage(UiText.error(R.string.error_sending_message, it)))
            }
        }
    }

    override fun onCleared() {
        if (activeChatTracker.activeChatId == chatId) activeChatTracker.activeChatId = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                GroupChatViewModel(
                    createSavedStateHandle(),
                    appContainer.authRepository,
                    appContainer.chatRepository,
                    appContainer.userRepository,
                    appContainer.messageRepository,
                    appContainer.activeChatTracker
                )
            }
        }
    }
}
