package com.example.aplikacjatypumessenger.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.databinding.ItemGroupMemberBinding
import com.example.aplikacjatypumessenger.viewmodels.GroupMemberItem

/** Lista członków grupy; admin ma przy każdej osobie menu z opcjami. */
class GroupMemberAdapter(
    private val onRemove: (GroupMemberItem) -> Unit,
    private val onMakeAdmin: (GroupMemberItem) -> Unit
) : ListAdapter<GroupMemberItem, GroupMemberAdapter.MemberViewHolder>(DiffCallback) {

    /** Czy bieżący użytkownik jest adminem i może zarządzać członkami. */
    var canManage: Boolean = false
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field == value) return
            field = value
            // Zmienia się wygląd wszystkich wierszy naraz (przycisk opcji), więc odświeżamy całą listę
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder =
        MemberViewHolder(ItemGroupMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MemberViewHolder(private val binding: ItemGroupMemberBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(member: GroupMemberItem) {
            val context = binding.root.context
            binding.userNameText.text = if (member.isCurrentUser) {
                context.getString(R.string.member_you, member.user.username)
            } else {
                member.user.username
            }
            val status = context.getString(if (member.user.isOnline) R.string.status_online else R.string.status_offline)
            binding.userStatusText.text =
                if (member.isAdmin) context.getString(R.string.member_admin_status, status) else status

            // Admin nie może usunąć sam siebie z tego menu - do tego służy „Opuść grupę”
            binding.optionsButton.visibility = if (canManage && !member.isCurrentUser) View.VISIBLE else View.GONE
            binding.optionsButton.setOnClickListener { showOptionsMenu(member) }
        }

        private fun showOptionsMenu(member: GroupMemberItem) {
            PopupMenu(binding.root.context, binding.optionsButton).apply {
                menu.add(R.string.action_remove_member).setOnMenuItemClickListener {
                    onRemove(member)
                    true
                }
                menu.add(R.string.action_make_admin).setOnMenuItemClickListener {
                    onMakeAdmin(member)
                    true
                }
                show()
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<GroupMemberItem>() {
        override fun areItemsTheSame(oldItem: GroupMemberItem, newItem: GroupMemberItem) = oldItem.user.id == newItem.user.id
        override fun areContentsTheSame(oldItem: GroupMemberItem, newItem: GroupMemberItem) = oldItem == newItem
    }
}
