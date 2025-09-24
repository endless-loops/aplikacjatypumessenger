// app/src/main/java/com/example/aplikacjatypumessenger/viewmodels/ChatListViewModel.kt

package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikacjatypumessenger.models.Chat
import com.example.aplikacjatypumessenger.models.Group
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.repositories.ChatRepository
import com.example.aplikacjatypumessenger.repositories.GroupRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val chatRepository: ChatRepository = ChatRepository(),
    private val groupRepository: GroupRepository = GroupRepository()
) : ViewModel() {

    val chats: StateFlow<List<Chat>> = chatRepository.chats
    val users: StateFlow<List<User>> = chatRepository.users
    val groups: StateFlow<List<Group>> = groupRepository.groups

    init {
        chatRepository.startListeningForChatsAndUsers()
        groupRepository.startListeningForUserGroups()
    }

    fun createPrivateChat(otherUserId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val chatId = chatRepository.createPrivateChat(otherUserId)
                onSuccess(chatId)
            } catch (e: Exception) {
                onError(e.message ?: "Błąd tworzenia czatu")
            }
        }
    }

    // ✅ POPRAWIONE: Tworzenie grupy z obsługą Result
    fun createGroup(name: String, participantIds: List<String>, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = groupRepository.createGroup(name, participantIds)
            if (result.isSuccess) {
                onSuccess(result.getOrNull() ?: "")
            } else {
                onError(result.exceptionOrNull()?.message ?: "Błąd tworzenia grupy")
            }
        }
    }

    // ✅ POPRAWIONE: Pobieranie szczegółów grupy
    fun getGroupDetails(groupId: String, onResult: (Group?) -> Unit) {
        viewModelScope.launch {
            try {
                val group = groupRepository.getGroupDetails(groupId)
                onResult(group)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    // ✅ POPRAWIONE: Pobieranie nazwy czatu
    fun getChatName(chat: Chat, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val name = if (chat.isGroup) {
                    val group = groupRepository.getGroupDetails(chat.id)
                    group?.name ?: "Grupa"
                } else {
                    chatRepository.getChatName(chat)
                }
                onResult(name)
            } catch (e: Exception) {
                onResult(if (chat.isGroup) "Grupa" else "Czat")
            }
        }
    }

    fun getFilteredUsers(query: String): List<User> {
        val currentUsers = users.value
        return if (query.isEmpty()) {
            currentUsers
        } else {
            currentUsers.filter {
                it.username.contains(query, ignoreCase = true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatRepository.stopListening()
        groupRepository.stopListening()
    }
}