package com.example.aplikacjatypumessenger.viewmodels

import com.example.aplikacjatypumessenger.models.Message
import com.example.aplikacjatypumessenger.repositories.MessageRepository
import com.example.aplikacjatypumessenger.utils.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Oznacza jako przeczytane wiadomości od innych osób, ale dopiero wtedy, gdy ekran czatu
 * jest faktycznie widoczny. Wspólne dla czatu prywatnego i grupowego.
 */
fun CoroutineScope.launchReadReceipts(
    messages: Flow<List<Message>>,
    isVisible: Flow<Boolean>,
    currentUserId: String,
    messageRepository: MessageRepository
): Job = launch {
    val readRequested = mutableSetOf<String>()
    combine(messages, isVisible) { messages, visible ->
        if (visible) unreadIncomingMessageIds(messages, currentUserId) else emptyList()
    }.collect { ids ->
        val newIds = ids.filterNot { it in readRequested }
        if (newIds.isEmpty()) return@collect
        readRequested += newIds
        launch {
            runSuspendCatching { messageRepository.markAsRead(newIds) }
                .onFailure { readRequested -= newIds.toSet() }
        }
    }
}
