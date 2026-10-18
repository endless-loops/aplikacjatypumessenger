package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.ChatRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateGroupUiState(
    val users: List<SelectableUserItem> = emptyList(),
    val selectedCount: Int = 0,
    val isSaving: Boolean = false
)

sealed interface CreateGroupEvent {
    data class ShowMessage(val text: UiText) : CreateGroupEvent
    data class OpenGroupChat(val chatId: String) : CreateGroupEvent
}

class CreateGroupViewModel(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val currentUserId = authRepository.currentUserId.orEmpty()
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val isSaving = MutableStateFlow(false)

    private val _events = Channel<CreateGroupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val users = userRepository.observeUsers().catch { e ->
        _events.send(CreateGroupEvent.ShowMessage(UiText.error(R.string.error_loading_users, e)))
        emit(emptyList())
    }

    val uiState: StateFlow<CreateGroupUiState> =
        combine(users, selectedIds, isSaving) { users, selected, saving ->
            CreateGroupUiState(
                users = filterUsers(users, currentUserId, query = "").toSelectableItems(selected),
                selectedCount = selected.size,
                isSaving = saving
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateGroupUiState())

    fun toggle(userId: String) = selectedIds.update { it.toggle(userId) }

    fun createGroup(name: String) {
        val trimmed = name.trim()
        val selected = selectedIds.value
        when {
            trimmed.isEmpty() -> _events.trySend(CreateGroupEvent.ShowMessage(UiText.Res(R.string.error_group_name_empty)))
            selected.size < MIN_OTHER_MEMBERS -> _events.trySend(
                CreateGroupEvent.ShowMessage(UiText.Res(R.string.error_group_too_few_members, listOf(MIN_OTHER_MEMBERS)))
            )
            isSaving.value -> Unit
            else -> viewModelScope.launch {
                isSaving.value = true
                runSuspendCatching { chatRepository.createGroup(trimmed, currentUserId, selected) }
                    .onSuccess { _events.send(CreateGroupEvent.OpenGroupChat(it)) }
                    .onFailure { _events.send(CreateGroupEvent.ShowMessage(UiText.error(R.string.error_creating_group, it))) }
                isSaving.value = false
            }
        }
    }

    companion object {
        private const val MIN_OTHER_MEMBERS = 2

        val Factory = viewModelFactory {
            initializer {
                CreateGroupViewModel(
                    appContainer.authRepository,
                    appContainer.userRepository,
                    appContainer.chatRepository
                )
            }
        }
    }
}
