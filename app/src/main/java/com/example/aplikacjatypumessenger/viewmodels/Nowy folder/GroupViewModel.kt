// app/src/main/java/com/example/aplikacjatypumessenger/viewmodels/GroupViewModel.kt

package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikacjatypumessenger.models.Group
import com.example.aplikacjatypumessenger.repositories.GroupRepository
import com.example.aplikacjatypumessenger.repositories.MessageRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GroupViewModel(
    private val groupRepository: GroupRepository = GroupRepository(),
    private val messageRepository: MessageRepository = MessageRepository()
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository.groups
    val messages: StateFlow<List<com.example.aplikacjatypumessenger.models.Message>> = messageRepository.messages

    // Grupy
    fun startListeningForUserGroups() {
        groupRepository.startListeningForUserGroups()
    }

    fun createGroup(name: String, participantIds: List<String>, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = groupRepository.createGroup(name, participantIds)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun getGroupDetails(groupId: String, onResult: (Group?) -> Unit) {
        viewModelScope.launch {
            val group = groupRepository.getGroupDetails(groupId)
            onResult(group)
        }
    }

    // Wiadomości grupowe
    fun startListeningForGroupMessages(groupId: String) {
        messageRepository.startListeningForGroupMessages(groupId)
    }

    fun sendGroupMessage(groupId: String, text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = messageRepository.sendGroupMessage(groupId, text)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    public override fun onCleared() {
        super.onCleared()
        groupRepository.stopListening()
        messageRepository.stopListening()
    }
}