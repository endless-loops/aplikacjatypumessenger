package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikacjatypumessenger.models.Group
import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.repositories.GroupRepository
import com.example.aplikacjatypumessenger.repositories.MessageRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GroupViewModel(
    private val groupRepository: GroupRepository = GroupRepository(),
    private val messageRepository: MessageRepository = MessageRepository()
) : ViewModel() {

    val groups: StateFlow<List<Group>> = groupRepository.groups
    val messages: StateFlow<List<Message>> = messageRepository.messages

    fun startListeningForUserGroups() {
        groupRepository.startListeningForUserGroups()
    }

    fun getGroupDetails(groupId: String, onResult: (Group?) -> Unit) {
        viewModelScope.launch {
            val group = groupRepository.getGroupDetails(groupId)
            onResult(group)
        }
    }

    fun startListeningForGroupMessages(groupId: String) {
        messageRepository.startListeningForGroupMessages(groupId)
    }

    fun sendGroupMessage(groupId: String, text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = messageRepository.sendGroupMessage(groupId, text)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun leaveGroup(groupId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = groupRepository.leaveGroup(groupId)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun removeMember(groupId: String, userId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = groupRepository.removeUserFromGroup(groupId, userId)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun getGroupMembers(
        participantIds: List<String>,
        onResult: (List<com.example.aplikacjatypumessenger.models.User>) -> Unit
    ) {
        viewModelScope.launch {
            val users = groupRepository.getMemberUsers(participantIds)
            onResult(users)
        }
    }

    override fun onCleared() {
        super.onCleared()
        groupRepository.stopListening()
        messageRepository.stopListening()
    }
}
