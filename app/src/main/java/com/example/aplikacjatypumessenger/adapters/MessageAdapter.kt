package com.example.aplikacjatypumessenger.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val messages: MutableList<Message>,
    private val currentUserId: String
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val TAG = "MessageAdapter"
    }

    override fun getItemViewType(position: Int): Int {
        val viewType = if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        Log.d(TAG, "Message at position $position: viewType=$viewType, senderId=${messages[position].senderId}, currentUserId=$currentUserId")
        return viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }

        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        Log.d(TAG, "Created ViewHolder with viewType: $viewType")
        return MessageViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        if (position < messages.size) {
            val message = messages[position]
            Log.d(TAG, "Binding message at position $position: '${message.text}' status='${message.status}' from ${message.senderId}")
            holder.bind(message)
        } else {
            Log.e(TAG, "Invalid position $position, messages size: ${messages.size}")
        }
    }

    override fun getItemCount(): Int {
        Log.d(TAG, "getItemCount: ${messages.size}")
        return messages.size
    }

    inner class MessageViewHolder(itemView: View, private val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private var messageTextView: TextView? = null
        private var timeTextView: TextView? = null
        private var statusImageView: ImageView? = null

        init {
            try {
                if (viewType == VIEW_TYPE_SENT) {
                    messageTextView = itemView.findViewById(R.id.sentMessageTextView)
                    timeTextView = itemView.findViewById(R.id.sentTimeTextView)
                    statusImageView = itemView.findViewById(R.id.messageStatusImageView)
                    Log.d(TAG, "Initialized sent message views: text=${messageTextView != null}, time=${timeTextView != null}, status=${statusImageView != null}")
                } else {
                    messageTextView = itemView.findViewById(R.id.receivedMessageTextView)
                    timeTextView = itemView.findViewById(R.id.receivedTimeTextView)
                    statusImageView = null // Explicitly set to null for received messages
                    Log.d(TAG, "Initialized received message views: text=${messageTextView != null}, time=${timeTextView != null}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing views", e)
            }
        }

        fun bind(message: Message) {
            try {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

                messageTextView?.text = message.text
                timeTextView?.text = time

                // Set status icon only for sent messages
                if (viewType == VIEW_TYPE_SENT && statusImageView != null) {
                    setMessageStatus(message.status, message)
                    Log.d(TAG, "Setting status for sent message: ${message.status}")
                } else if (viewType == VIEW_TYPE_SENT && statusImageView == null) {
                    Log.e(TAG, "StatusImageView is null for sent message!")
                }

                Log.d(TAG, "Successfully bound message: '${message.text}' at $time with status '${message.status}'")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding message", e)
            }
        }

        private fun setMessageStatus(status: String, message: Message) {
            statusImageView?.let { statusIcon ->
                // Determine the final status to display
                val finalStatus = when {
                    status.isNotEmpty() -> status.lowercase()
                    // Fallback for legacy messages
                    message.seen -> "read"
                    else -> "delivered"
                }

                Log.d(TAG, "Setting status icon for message: originalStatus='$status', finalStatus='$finalStatus'")

                // Clear any previous color filters and set visibility
                statusIcon.clearColorFilter()
                statusIcon.visibility = View.VISIBLE

                when (finalStatus) {
                    "sending" -> {
                        statusIcon.setImageResource(R.drawable.ic_clock)
                        statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                        Log.d(TAG, "Set sending status (clock icon)")
                    }
                    "sent" -> {
                        statusIcon.setImageResource(R.drawable.ic_check)
                        statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                        Log.d(TAG, "Set sent status (single check)")
                    }
                    "delivered" -> {
                        statusIcon.setImageResource(R.drawable.ic_double_check)
                        statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                        Log.d(TAG, "Set delivered status (double check gray)")
                    }
                    "read" -> {
                        statusIcon.setImageResource(R.drawable.ic_double_check)
                        statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.holo_blue_bright))
                        Log.d(TAG, "Set read status (double check blue)")
                    }
                    "failed", "error" -> {
                        statusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                        statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.holo_red_light))
                        Log.d(TAG, "Set failed status (alert icon)")
                    }
                    else -> {
                        // Default to sent status for unknown status
                        statusIcon.setImageResource(R.drawable.ic_check)
                        statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                        Log.w(TAG, "Unknown status '$finalStatus', defaulting to sent status")
                    }
                }
            } ?: Log.e(TAG, "Status ImageView is null!")
        }
    }

    // Method to update entire message list
    fun updateMessages(newMessages: List<Message>) {
        Log.d(TAG, "Updating messages: ${newMessages.size} messages")
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    // Method to add a single message
    fun addMessage(message: Message) {
        Log.d(TAG, "Adding message: '${message.text}' with status '${message.status}' from ${message.senderId}")
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    // Method to update a specific message (improved)
    fun updateMessage(message: Message) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index != -1) {
            Log.d(TAG, "Updating message at index $index: '${message.text}' status changed to '${message.status}'")
            val oldMessage = messages[index]
            messages[index] = message

            // Only notify if something actually changed
            if (oldMessage.status != message.status || oldMessage.seen != message.seen) {
                notifyItemChanged(index)
            }
        } else {
            Log.w(TAG, "Message to update not found: ${message.id}")
        }
    }

    // Method to update only message status (more efficient)
    fun updateMessageStatus(messageId: String, newStatus: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val message = messages[index]
            if (message.status != newStatus) {
                Log.d(TAG, "Updating status for message $messageId from '${message.status}' to '$newStatus'")
                message.status = newStatus
                notifyItemChanged(index)
            }
        } else {
            Log.w(TAG, "Message to update status not found: $messageId")
        }
    }

    // Method to get current messages (for debugging)
    fun getCurrentMessages(): List<Message> = messages.toList()
}