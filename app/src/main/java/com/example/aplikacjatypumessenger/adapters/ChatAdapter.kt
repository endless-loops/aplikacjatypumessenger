package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.Chat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val currentUserId: String = "",
    private val onChatClick: (Chat) -> Unit
) : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chatNameTextView: TextView = itemView.findViewById(R.id.chatNameTextView)
        private val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)

        fun bind(chat: Chat) {
            // Wyświetl pełną nazwę (chatName dla 1:1, groupName dla grup)
            chatNameTextView.text = when {
                chat.isGroup -> chat.groupName.ifEmpty { "Grupa" }
                chat.chatName.isNotEmpty() -> chat.chatName
                else -> "Czat"
            }

            val rawText = chat.lastMessageText
            lastMessageTextView.text = if (rawText.isEmpty()) {
                "Brak wiadomości"
            } else {
                val senderId = chat.lastMessage?.get("senderId") as? String ?: ""
                when {
                    senderId == currentUserId -> "Ty: $rawText"
                    chat.isGroup && senderId.isNotEmpty() -> rawText  // nazwa nadawcy jest w MessageAdapter
                    else -> rawText
                }
            }

            timeTextView.text = if (chat.lastMessageTime > 0) {
                formatChatTime(chat.lastMessageTime)
            } else {
                ""
            }

            itemView.setOnClickListener { onChatClick(chat) }
        }

        private fun formatChatTime(timestamp: Long): String {
            val now = Calendar.getInstance()
            val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

            return when {
                isSameDay(now, msgCal) ->
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                isYesterday(now, msgCal) ->
                    "Wczoraj"
                isSameYear(now, msgCal) ->
                    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
                else ->
                    SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(timestamp))
            }
        }

        private fun isSameDay(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        private fun isYesterday(today: Calendar, other: Calendar): Boolean {
            val yesterday = Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, -1)
            }
            return isSameDay(yesterday, other)
        }

        private fun isSameYear(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
    }
}

class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
    override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean =
        oldItem.id == newItem.id &&
                oldItem.chatName == newItem.chatName &&
                oldItem.groupName == newItem.groupName &&
                oldItem.lastMessageText == newItem.lastMessageText &&
                oldItem.lastMessageTime == newItem.lastMessageTime
}
