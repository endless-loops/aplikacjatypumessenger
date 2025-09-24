package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.Message
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUserId: String,
    private var isGroupChat: Boolean = false
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<Message>()
    private val userCache = mutableMapOf<String, String>() // ✅ CACHE nazw użytkowników
    private val db = FirebaseFirestore.getInstance()
    private var onSenderNameClick: ((String) -> Unit)? = null

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    fun updateList(newMessages: List<Message>, isGroup: Boolean = false) {
        this.isGroupChat = isGroup
        messages.clear()
        messages.addAll(newMessages)

        // ✅ Załaduj nazwy użytkowników dla nowych wiadomości
        loadUserNamesForMessages()

        notifyDataSetChanged()
    }

    // ✅ ŁADUJ NAZWY UŻYTKOWNIKÓW
    private fun loadUserNamesForMessages() {
        val userIds = messages
            .filter { it.senderId != currentUserId } // Tylko inni użytkownicy
            .map { it.senderId }
            .distinct()
            .filter { !userCache.containsKey(it) } // Tylko niezaładowani

        if (userIds.isEmpty()) return

        userIds.forEach { userId ->
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    val username = document.getString("username") ?: "Użytkownik"
                    userCache[userId] = username

                    // ✅ Odśwież tylko wiersze z tym użytkownikiem
                    val positions = messages.mapIndexed { index, message ->
                        if (message.senderId == userId) index else -1
                    }.filter { it != -1 }

                    positions.forEach { position ->
                        notifyItemChanged(position)
                    }
                }
                .addOnFailureListener {
                    userCache[userId] = "Użytkownik" // Fallback
                }
        }
    }

    // ✅ POBERZ NAZWĘ UŻYTKOWNIKA Z CACHE
    private fun getUserName(userId: String): String {
        return userCache[userId] ?: "Użytkownik" // Fallback
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }

        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return MessageViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message, isGroupChat, ::getUserName) // ✅ Przekazujemy funkcję getUserName
    }

    override fun getItemCount(): Int = messages.size

    fun setOnSenderNameClickListener(listener: (String) -> Unit) {
        this.onSenderNameClick = listener
    }

    inner class MessageViewHolder(itemView: View, private val viewType: Int) :
        RecyclerView.ViewHolder(itemView) {

        private val messageText: TextView? = itemView.findViewById(
            if (viewType == VIEW_TYPE_SENT) R.id.sentMessageTextView else R.id.receivedMessageTextView
        )

        private val timeText: TextView? = itemView.findViewById(
            if (viewType == VIEW_TYPE_SENT) R.id.sentTimeTextView else R.id.receivedTimeTextView
        )

        private val statusIcon: ImageView? = itemView.findViewById(R.id.messageStatusImageView)
        private val senderNameText: TextView? = itemView.findViewById(R.id.senderNameTextView)

        // ✅ DODAJEMY PARAMETR getUserNameFunction
        fun bind(message: Message, isGroupChat: Boolean, getUserName: (String) -> String) {
            messageText?.text = message.text
            timeText?.text = formatTime(message.timestamp)

            if (viewType == VIEW_TYPE_SENT) {
                setMessageStatus(message)
            }

            // ✅ TERAZ UŻYWAMY PRAWDZIWYCH NAZW
            if (isGroupChat && viewType == VIEW_TYPE_RECEIVED && senderNameText != null) {
                senderNameText.visibility = View.VISIBLE
                val username = getUserName(message.senderId) // ✅ Prawdziwa nazwa użytkownika
                senderNameText.text = username
                senderNameText.setOnClickListener {
                    onSenderNameClick?.invoke(message.senderId)
                }
            } else {
                senderNameText?.visibility = View.GONE
            }
        }

        private fun setMessageStatus(message: Message) {
            statusIcon?.let { icon ->
                val status = when {
                    message.seen -> "read"
                    message.status == "delivered" -> "delivered"
                    message.status == "sent" -> "sent"
                    else -> "sending"
                }

                when (status) {
                    "sending" -> icon.setImageResource(R.drawable.ic_clock)
                    "sent" -> icon.setImageResource(R.drawable.ic_check)
                    "delivered", "read" -> icon.setImageResource(R.drawable.ic_double_check)
                }

                val color = when (status) {
                    "read" -> android.R.color.holo_blue_bright
                    else -> android.R.color.darker_gray
                }
                icon.setColorFilter(ContextCompat.getColor(itemView.context, color))
                icon.visibility = View.VISIBLE
            }
        }

        private fun formatTime(timestamp: Long): String {
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}