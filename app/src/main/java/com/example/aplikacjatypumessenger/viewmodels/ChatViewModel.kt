// app/src/main/java/com/example/aplikacjatypumessenger/viewmodels/ChatViewModel.kt

package com.example.aplikacjatypumessenger.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.repositories.MessageRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val messageRepository: MessageRepository = MessageRepository()
) : ViewModel() {

    val messages: StateFlow<List<Message>> = messageRepository.messages

    fun startListening(chatId: String) {
        messageRepository.startListeningForMessages(chatId)
    }

    fun sendMessage(chatId: String, otherUserId: String, text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = messageRepository.sendMessage(chatId, otherUserId, text)
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    fun markMessagesAsRead(chatId: String, userId: String) {
        viewModelScope.launch {
            messageRepository.markMessagesAsRead(chatId, userId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageRepository.stopListening()
    }
}