package com.example.aplikacjatypumessenger.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.ChatRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
import com.example.aplikacjatypumessenger.utils.Extras
import com.example.aplikacjatypumessenger.utils.UiText
import com.example.aplikacjatypumessenger.utils.appContainer
import com.example.aplikacjatypumessenger.utils.runSuspendCatching
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Członek grupy na liście w ekranie zarządzania. */
data class GroupMemberItem(
    val user: User,
    val isAdmin: Boolean,
    val isCurrentUser: Boolean
)

data class GroupManagementUiState(
    val groupName: String? = null,
    val members: List<GroupMemberItem> = emptyList(),
    /** Od tego zależy, czy widać opcje admina (usuwanie członków, usunięcie grupy itd.). */
    val isCurrentUserAdmin: Boolean = false,
    /** Trwa jakaś operacja - przyciski są wtedy nieaktywne. */
    val isBusy: Boolean = false
)

sealed interface GroupManagementEvent {
    data class ShowMessage(val text: UiText) : GroupManagementEvent
    data object Close : GroupManagementEvent
}

/**
 * Ekran „Informacje i członkowie”: lista członków, dodawanie i usuwanie osób,
 * przekazanie roli admina, opuszczenie i usunięcie grupy. Uprawnienia admina sprawdzają
 * też reguły Firestore - ukrycie przycisków w UI to tylko wygoda, a nie zabezpieczenie.
 */
@OptIn(ExperimentalCoroutinesApi::class) // mapLatest
class GroupManagementViewModel(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val chatId: String = savedStateHandle[Extras.CHAT_ID] ?: ""
    private val currentUserId = authRepository.currentUserId.orEmpty()
    private val isBusy = MutableStateFlow(false)

    private val _events = Channel<GroupManagementEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Ekran zamyka się sam, gdy grupa zniknie albo użytkownik zostanie z niej usunięty
    private val chat: StateFlow<Chat?> = chatRepository.observeChat(chatId)
        .onEach { chat ->
            if (chat == null || currentUserId !in chat.participants) _events.send(GroupManagementEvent.Close)
        }
        .catch { _events.send(GroupManagementEvent.Close) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val members = chat.filterNotNull()
        .map { it.participants }
        .distinctUntilChanged()
        .mapLatest { ids -> userRepository.getUsers(ids) }
        .catch { e ->
            _events.send(GroupManagementEvent.ShowMessage(UiText.error(R.string.error_loading_members, e)))
            emit(emptyList())
        }

    val uiState: StateFlow<GroupManagementUiState> =
        combine(chat, members, isBusy) { chat, members, busy ->
            val adminId = chat?.groupAdmin
            GroupManagementUiState(
                groupName = chat?.groupName,
                members = members
                    .map { GroupMemberItem(it, isAdmin = it.id == adminId, isCurrentUser = it.id == currentUserId) }
                    // Admin zawsze na górze listy, reszta alfabetycznie
                    .sortedWith(compareByDescending<GroupMemberItem> { it.isAdmin }.thenBy { it.user.username.lowercase() }),
                isCurrentUserAdmin = adminId == currentUserId,
                isBusy = busy
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupManagementUiState())

    fun removeMember(userId: String) = runAction(R.string.error_removing_member) {
        chatRepository.removeMember(chatId, userId)
    }

    fun makeAdmin(userId: String) = runAction(R.string.error_changing_admin) {
        chatRepository.setAdmin(chatId, userId)
    }

    fun leaveGroup() = runAction(R.string.error_leaving_group) {
        val chat = chat.value ?: return@runAction
        chatRepository.leaveGroup(chat, currentUserId)
        _events.send(GroupManagementEvent.Close)
    }

    fun deleteGroup() = runAction(R.string.error_deleting_group) {
        chatRepository.deleteGroup(chatId)
        _events.send(GroupManagementEvent.Close)
    }

    /** Wspólna obsługa akcji: blokada na czas operacji i komunikat przy błędzie. */
    private fun runAction(@StringRes errorRes: Int, action: suspend () -> Unit) {
        if (isBusy.value) return
        viewModelScope.launch {
            isBusy.value = true
            runSuspendCatching { action() }
                .onFailure { _events.send(GroupManagementEvent.ShowMessage(UiText.error(errorRes, it))) }
            isBusy.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                GroupManagementViewModel(
                    createSavedStateHandle(),
                    appContainer.authRepository,
                    appContainer.chatRepository,
                    appContainer.userRepository
                )
            }
        }
    }
}
