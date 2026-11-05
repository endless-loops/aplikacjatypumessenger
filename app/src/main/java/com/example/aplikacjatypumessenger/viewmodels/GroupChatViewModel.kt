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
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Imiona członków, którzy przeczytali / jeszcze nie przeczytali wybranej wiadomości. */
    data class ShowReadReceipts(val readBy: List<String?>, val notReadBy: List<String?>) : GroupChatEvent
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

    private val isVisible = MutableStateFlow(false)

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
    private val usersById: StateFlow<Map<String, User>> =
        combine(chat.filterNotNull(), messages) { chat, messages ->
            chat.participants.toSet() + messages.map { it.senderId } + messages.flatMap { it.recipients }
        }
            .distinctUntilChanged()
            .mapLatest { ids -> userRepository.getUsers(ids).associateBy { it.id } }
            .catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val uiState: StateFlow<GroupChatUiState> =
        combine(chat, messages, usersById) { chat, messages, users ->
            GroupChatUiState(
                groupName = chat?.groupName,
                memberCount = chat?.participants?.size ?: 0,
                messages = messages.map { message ->
                    message.toItem(
                        currentUserId,
                        senderName = users[message.senderId]?.username,
                        currentMembers = chat?.participants?.toSet()
                    )
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChatUiState())

    init {
        viewModelScope.launchReadReceipts(messages, isVisible, currentUserId, messageRepository)
    }

    fun onScreenVisibilityChanged(visible: Boolean) {
        isVisible.value = visible
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
                val recipients = chat.value?.participants.orEmpty().filter { it != currentUserId }.distinct()
                messageRepository.send(chatId, currentUserId, trimmed, isGroup = true, recipients = recipients)
            }.onFailure {
                _events.send(GroupChatEvent.ShowMessage(UiText.error(R.string.error_sending_message, it)))
            }
        }
    }

    /** Przytrzymanie własnej wiadomości pokazuje, kto ją przeczytał. */
    fun onMessageLongClick(messageId: String) {
        val message = messages.value.firstOrNull { it.id == messageId } ?: return
        if (message.senderId != currentUserId) return
        val members = chat.value?.participants.orEmpty()
        viewModelScope.launch {
            val receipts = message.readReceipts(members)
            if (receipts == null) {
                _events.send(GroupChatEvent.ShowMessage(UiText.Res(R.string.read_receipts_unavailable)))
                return@launch
            }
            val users = usersById.value
            _events.send(
                GroupChatEvent.ShowReadReceipts(
                    readBy = receipts.readBy.map { users[it]?.username },
                    notReadBy = receipts.notReadBy.map { users[it]?.username }
                )
            )
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
