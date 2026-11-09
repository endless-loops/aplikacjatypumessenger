package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.databinding.ItemUserBinding
import com.example.aplikacjatypumessenger.models.User
import com.example.aplikacjatypumessenger.utils.TimeFormat

/** Lista użytkowników (zakładka Użytkownicy) ze statusem online / ostatnią aktywnością. */
class UserAdapter(
    private val onUserClick: (User) -> Unit
) : ListAdapter<User, UserAdapter.UserViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder =
        UserViewHolder(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            val context = binding.root.context
            binding.userNameTextView.text = user.username
            binding.userStatusTextView.text =
                if (user.isOnline) context.getString(R.string.status_online) else formatLastSeen(user.lastSeen)
            binding.statusIndicator.setColorFilter(
                ContextCompat.getColor(context, if (user.isOnline) R.color.online_green else R.color.offline_gray)
            )
            binding.root.setOnClickListener { onUserClick(user) }
        }

        /** „Aktywny przed chwilą / X min temu / X godz. temu”, a dla starszych dat konkretna data. */
        private fun formatLastSeen(timestamp: Long): String {
            val context = binding.root.context
            if (timestamp <= 0L) return context.getString(R.string.status_offline)
            val minutes = (System.currentTimeMillis() - timestamp) / 60_000
            val hours = minutes / 60
            val ago = when {
                minutes < 1 -> context.getString(R.string.last_seen_now)
                minutes < 60 -> context.getString(R.string.last_seen_minutes, minutes)
                hours < 24 -> context.getString(R.string.last_seen_hours, hours)
                else -> TimeFormat.dateTime(timestamp)
            }
            return context.getString(R.string.last_seen, ago)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
