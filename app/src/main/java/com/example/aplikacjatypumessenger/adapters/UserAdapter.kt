package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserAdapter(
    private val onUserClick: (User) -> Unit
) : ListAdapter<User, UserAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameText: TextView = itemView.findViewById(R.id.userNameTextView)
        private val userStatusText: TextView = itemView.findViewById(R.id.userStatusTextView)
        private val statusIndicator: ImageView = itemView.findViewById(R.id.statusIndicator)

        fun bind(user: User) {
            userNameText.text = user.username

            // Użytkownik jest "online" tylko jeśli status="online" ORAZ
            // lastSeen jest świeże (< 3 minuty) — chroni przed starymi/zapomnianymi statusami
            val isRecentlyActive = (System.currentTimeMillis() - user.lastSeen) < 3 * 60 * 1000
            val isOnline = user.status == "online" && isRecentlyActive

            if (isOnline) {
                userStatusText.text = "Online"
                statusIndicator.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.online_green)
                )
            } else {
                userStatusText.text = "Ostatnio: ${formatLastSeen(user.lastSeen)}"
                statusIndicator.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.offline_gray)
                )
            }

            itemView.setOnClickListener { onUserClick(user) }
        }

        private fun formatLastSeen(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val minutes = diff / (1000 * 60)
            val hours = diff / (1000 * 60 * 60)

            return when {
                minutes < 1 -> "teraz"
                minutes < 60 -> "$minutes min temu"
                hours < 24 -> "$hours godz. temu"
                else -> SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}

class UserDiffCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem == newItem
    }
}
