package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.databinding.ItemChatBinding
import com.example.aplikacjatypumessenger.utils.TimeFormat
import com.example.aplikacjatypumessenger.viewmodels.ChatListItem

class ChatAdapter(
    private val onChatClick: (ChatListItem) -> Unit
) : ListAdapter<ChatListItem, ChatAdapter.ChatViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder =
        ChatViewHolder(ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chat: ChatListItem) {
            val context = binding.root.context
            binding.chatNameTextView.text = chat.title
                ?: context.getString(if (chat.isGroup) R.string.default_group_name else R.string.default_user_name)
            binding.lastMessageTextView.text = chat.lastMessage ?: context.getString(R.string.no_messages)
            binding.timeTextView.text = TimeFormat.chatListTime(chat.lastMessageTime)
            binding.root.setOnClickListener { onChatClick(chat) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ChatListItem>() {
        override fun areItemsTheSame(oldItem: ChatListItem, newItem: ChatListItem) = oldItem.chatId == newItem.chatId
        override fun areContentsTheSame(oldItem: ChatListItem, newItem: ChatListItem) = oldItem == newItem
    }
}
