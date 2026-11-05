package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.databinding.ItemMessageReceivedBinding
import com.example.aplikacjatypumessenger.databinding.ItemMessageSentBinding
import com.example.aplikacjatypumessenger.models.MessageStatus
import com.example.aplikacjatypumessenger.utils.TimeFormat
import com.example.aplikacjatypumessenger.viewmodels.MessageItem

/**
 * @param showSenderNames w czatach grupowych nad cudzymi wiadomościami pokazuje autora
 * @param onOwnMessageLongClick wywoływane po przytrzymaniu własnej wiadomości
 */
class MessageAdapter(
    private val showSenderNames: Boolean = false,
    private val onOwnMessageLongClick: ((MessageItem) -> Unit)? = null
) : ListAdapter<MessageItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isMine) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
        } else {
            ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
        }
    }

    inner class SentViewHolder(private val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            if (onOwnMessageLongClick != null) {
                binding.root.setOnLongClickListener {
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                    onOwnMessageLongClick.invoke(getItem(position))
                    true
                }
            }
        }

        fun bind(message: MessageItem) {
            binding.sentMessageTextView.text = message.text
            binding.sentTimeTextView.text = TimeFormat.time(message.timestamp)

            val (icon, color) = when (message.status) {
                MessageStatus.SENDING -> R.drawable.ic_clock to android.R.color.darker_gray
                MessageStatus.DELIVERED -> R.drawable.ic_double_check to android.R.color.darker_gray
                MessageStatus.READ -> R.drawable.ic_double_check to android.R.color.holo_blue_bright
                else -> R.drawable.ic_check to android.R.color.darker_gray
            }
            binding.messageStatusImageView.setImageResource(icon)
            binding.messageStatusImageView.setColorFilter(ContextCompat.getColor(binding.root.context, color))
        }
    }

    inner class ReceivedViewHolder(private val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageItem) {
            binding.receivedMessageTextView.text = message.text
            binding.receivedTimeTextView.text = TimeFormat.time(message.timestamp)
            binding.senderNameTextView.visibility = if (showSenderNames) View.VISIBLE else View.GONE
            binding.senderNameTextView.text =
                message.senderName ?: binding.root.context.getString(R.string.default_user_name)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MessageItem>() {
        override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem) = oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_SENT = 1
        const val VIEW_TYPE_RECEIVED = 2
    }
}
