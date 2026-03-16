package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.Message
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUserId: String,
    private var isGroupChat: Boolean = false
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<Message>()
    private val userCache = mutableMapOf<String, String>()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    fun updateList(newMessages: List<Message>) {
        val diffCallback = MessageDiffCallback(messages, newMessages)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        // Załaduj nazwy dla nowych wiadomości zanim odświeżymy listę
        val newUserIds = newMessages
            .filter { it.senderId != currentUserId }
            .map { it.senderId }
            .distinct()
            .filter { !userCache.containsKey(it) }

        messages.clear()
        messages.addAll(newMessages)
        diffResult.dispatchUpdatesTo(this)

        newUserIds.forEach { userId ->
            db.collection("users").document(userId).get()
                .addOnSuccessListener { doc ->
                    val username = doc.getString("username") ?: "Użytkownik"
                    userCache[userId] = username
                    // Odśwież tylko pozycje tego nadawcy
                    messages.forEachIndexed { index, msg ->
                        if (msg.senderId == userId) notifyItemChanged(index)
                    }
                }
                .addOnFailureListener {
                    userCache[userId] = "Użytkownik"
                }
        }
    }

    private fun getUserName(userId: String): String = userCache[userId] ?: "..."

    override fun getItemViewType(position: Int): Int =
        if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_SENT)
            R.layout.item_message_sent else R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return MessageViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val showDate = position == 0 || !isSameDay(messages[position - 1].timestamp, message.timestamp)
        holder.bind(message, isGroupChat, showDate, ::getUserName)
    }

    override fun getItemCount(): Int = messages.size

    private fun isSameDay(ts1: Long, ts2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
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

        fun bind(message: Message, isGroupChat: Boolean, showDate: Boolean, getUserName: (String) -> String) {
            messageText?.text = message.text
            timeText?.text = formatMessageTime(message.timestamp, showDate)

            if (viewType == VIEW_TYPE_SENT) {
                setMessageStatus(message)
            }

            if (isGroupChat && viewType == VIEW_TYPE_RECEIVED && senderNameText != null) {
                senderNameText.visibility = View.VISIBLE
                senderNameText.text = getUserName(message.senderId)
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
                val color = if (status == "read") R.color.online_green else android.R.color.darker_gray
                icon.setColorFilter(ContextCompat.getColor(itemView.context, color))
                icon.visibility = View.VISIBLE
            }
        }

        /**
         * Formatuje czas wiadomości:
         * - Dzisiaj: "HH:mm"
         * - Wczoraj: "Wczoraj HH:mm"
         * - Ten rok:  "dd MMM HH:mm"
         * - Starsze:  "dd.MM.yy HH:mm"
         */
        private fun formatMessageTime(timestamp: Long, showDate: Boolean): String {
            val now = Calendar.getInstance()
            val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val timePart = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

            if (!showDate) return timePart

            val isSameDay = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
            if (isSameDay) return timePart

            val yesterday = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis; add(Calendar.DAY_OF_YEAR, -1)
            }
            val isYesterday = yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
            if (isYesterday) return "Wczoraj $timePart"

            val isSameYear = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
            return if (isSameYear) {
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))
            } else {
                SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}

class MessageDiffCallback(
    private val oldList: List<Message>,
    private val newList: List<Message>
) : DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size
    override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos].id == newList[newPos].id
    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
        val old = oldList[oldPos]; val new = newList[newPos]
        return old.id == new.id && old.status == new.status && old.seen == new.seen
    }
}
