package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.ChatRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.appContainer
import com.example.aplikacjatypumessenger.utils.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class MainTab { CHATS, GROUPS, USERS, PROFILE }

data class MainUiState(
    val selectedTab: MainTab = MainTab.CHATS,
    val chats: List<ChatListItem> = emptyList(),
    val users: List<User> = emptyList(),
    val profile: User? = null,
    val email: String = ""
)

sealed interface MainEvent {
    data class OpenChat(val chatId: String, val isGroup: Boolean, val otherUserId: String?) : MainEvent
    data object NavigateToLogin : MainEvent
    data class ShowMessage(val text: UiText) : MainEvent
}

class MainViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    applicationScope: CoroutineScope
) : ViewModel() {

    private val currentUserId = authRepository.currentUserId.orEmpty()
    private val selectedTab = MutableStateFlow(MainTab.CHATS)
    private val query = MutableStateFlow("")

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val allUsers: Flow<List<User>> = userRepository.observeUsers()
        .catch { e ->
            _events.send(MainEvent.ShowMessage(UiText.error(R.string.error_loading_users, e)))
            emit(emptyList())
        }

    private val chats: Flow<List<Chat>> =
        if (currentUserId.isEmpty()) flowOf(emptyList())
        else chatRepository.observeChatsForUser(currentUserId).catch { e ->
            _events.send(MainEvent.ShowMessage(UiText.error(R.string.error_loading_chats, e)))
            emit(emptyList())
        }

    val uiState: StateFlow<MainUiState> =
        combine(selectedTab, chats, allUsers, query) { tab, chats, users, query ->
            val usersById = users.associateBy { it.id }
            val chatItems = buildChatListItems(chats, usersById, currentUserId)
            MainUiState(
                selectedTab = tab,
                chats = if (tab == MainTab.GROUPS) chatItems.filter { it.isGroup } else chatItems,
                users = filterUsers(users, currentUserId, query),
                profile = usersById[currentUserId],
                email = authRepository.currentUserEmail.orEmpty()
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MainUiState(email = authRepository.currentUserEmail.orEmpty())
        )

    init {
        if (currentUserId.isEmpty()) {
            _events.trySend(MainEvent.NavigateToLogin)
        } else {
            // Token FCM mógł się zmienić od ostatniego logowania
            applicationScope.launch {
                runSuspendCatching {
                    val token = userRepository.fetchFcmToken()
                    if (token.isNotEmpty()) userRepository.updateFcmToken(currentUserId, token)
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        selectedTab.value = tab
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun onUserClicked(user: User) {
        viewModelScope.launch {
            runSuspendCatching { chatRepository.findOrCreatePrivateChat(currentUserId, user.id) }
                .onSuccess { _events.send(MainEvent.OpenChat(it.id, isGroup = false, otherUserId = user.id)) }
                .onFailure { _events.send(MainEvent.ShowMessage(UiText.error(R.string.error_creating_chat, it))) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Bez połączenia zapis nie zostanie potwierdzony - nie blokujemy wylogowania
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) {
                runSuspendCatching { userRepository.onSigningOut(currentUserId) }
            }
            authRepository.logout()
            _events.send(MainEvent.NavigateToLogin)
        }
    }

    companion object {
        private const val LOGOUT_TIMEOUT_MS = 3_000L

        val Factory = viewModelFactory {
            initializer {
                MainViewModel(
                    appContainer.authRepository,
                    appContainer.userRepository,
                    appContainer.chatRepository,
                    appContainer.applicationScope
                )
            }
        }
    }
}
