package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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

class SelectableUserAdapter(
    private val onSelectionChanged: (selectedIds: Set<String>) -> Unit
) : ListAdapter<User, SelectableUserAdapter.SelectableUserViewHolder>(UserDiffCallback()) {

    private val selectedIds = mutableSetOf<String>()

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_selectable, parent, false)
        return SelectableUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: SelectableUserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SelectableUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameText: TextView = itemView.findViewById(R.id.userNameTextView)
        private val userStatusText: TextView = itemView.findViewById(R.id.userStatusTextView)
        private val statusIndicator: ImageView = itemView.findViewById(R.id.statusIndicator)
        private val checkBox: CheckBox = itemView.findViewById(R.id.userCheckBox)

        fun bind(user: User) {
            userNameText.text = user.username

            val isRecentlyActive = (System.currentTimeMillis() - user.lastSeen) < 3 * 60 * 1000
            val isOnline = user.status == "online" && isRecentlyActive

            if (isOnline) {
                userStatusText.text = "Online"
                statusIndicator.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.online_green)
                )
            } else {
                userStatusText.text = formatLastSeen(user.lastSeen)
                statusIndicator.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.offline_gray)
                )
            }

            checkBox.isChecked = selectedIds.contains(user.id)
            updateBackground(checkBox.isChecked)

            val toggle = View.OnClickListener {
                if (selectedIds.contains(user.id)) {
                    selectedIds.remove(user.id)
                    checkBox.isChecked = false
                } else {
                    selectedIds.add(user.id)
                    checkBox.isChecked = true
                }
                updateBackground(checkBox.isChecked)
                onSelectionChanged(selectedIds.toSet())
            }

            itemView.setOnClickListener(toggle)
            checkBox.setOnClickListener(toggle)
        }

        private fun updateBackground(selected: Boolean) {
            itemView.setBackgroundResource(
                if (selected) R.drawable.bg_item_selected
                else android.R.color.transparent
            )
        }

        private fun formatLastSeen(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            val minutes = diff / (1000 * 60)
            val hours = diff / (1000 * 60 * 60)
            return when {
                minutes < 1 -> "Aktywny teraz"
                minutes < 60 -> "Aktywny $minutes min temu"
                hours < 24 -> "Aktywny $hours godz. temu"
                else -> "Aktywny ${SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(timestamp))}"
            }
        }
    }
}
