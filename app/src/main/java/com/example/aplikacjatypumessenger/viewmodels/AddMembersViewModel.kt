package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.repositories.AuthRepository
import com.example.aplikacjatypumessenger.repositories.ChatRepository
import com.example.aplikacjatypumessenger.repositories.UserRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddMembersUiState(
    val groupName: String? = null,
    val users: List<SelectableUserItem> = emptyList(),
    val selectedCount: Int = 0,
    val isSaving: Boolean = false
)

sealed interface AddMembersEvent {
    data class ShowMessage(val text: UiText) : AddMembersEvent
    data class Done(val addedCount: Int) : AddMembersEvent
}

/** Dodawanie nowych osób do istniejącej grupy (dostępne dla admina). */
class AddMembersViewModel(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val chatId: String = savedStateHandle[Extras.CHAT_ID] ?: ""
    private val currentUserId = authRepository.currentUserId.orEmpty()

    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")
    private val isSaving = MutableStateFlow(false)

    private val _events = Channel<AddMembersEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val chat = chatRepository.observeChat(chatId).catch { emit(null) }

    private val users = userRepository.observeUsers().catch { e ->
        _events.send(AddMembersEvent.ShowMessage(UiText.error(R.string.error_loading_users, e)))
        emit(emptyList())
    }

    // Kandydaci = użytkownicy pasujący do wyszukiwania, którzy jeszcze nie są w grupie.
    // Lista odświeża się sama, jeśli ktoś inny w tym czasie zmieni skład grupy.
    private val candidates = combine(users, chat, query) { users, chat, query ->
        val members = chat?.participants.orEmpty().toSet()
        chat?.groupName to filterUsers(users, currentUserId, query).filter { it.id !in members }
    }

    val uiState: StateFlow<AddMembersUiState> =
        combine(candidates, selectedIds, isSaving) { (groupName, candidates), selected, saving ->
            AddMembersUiState(
                groupName = groupName,
                users = candidates.toSelectableItems(selected),
                selectedCount = selected.size,
                isSaving = saving
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddMembersUiState())

    fun toggle(userId: String) = selectedIds.update { it.toggle(userId) }

    fun setQuery(text: String) {
        query.value = text
    }

    fun addSelected() {
        val ids = selectedIds.value
        if (ids.isEmpty() || isSaving.value) return
        viewModelScope.launch {
            isSaving.value = true
            runSuspendCatching { chatRepository.addMembers(chatId, ids) }
                .onSuccess { _events.send(AddMembersEvent.Done(ids.size)) }
                .onFailure { _events.send(AddMembersEvent.ShowMessage(UiText.error(R.string.error_adding_members, it))) }
            isSaving.value = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                AddMembersViewModel(
                    createSavedStateHandle(),
                    appContainer.authRepository,
                    appContainer.userRepository,
                    appContainer.chatRepository
                )
            }
        }
    }
}
