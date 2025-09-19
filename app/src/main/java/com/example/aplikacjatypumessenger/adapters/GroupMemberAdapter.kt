package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacatypumessenger.R
import com.example.aplikacatypumessenger.models.User

class GroupMemberAdapter(
    private val members: List<User>,
    var isAdmin: Boolean,
    private val currentUserId: String,  // 👈 PRZEKAŻ ID PRZEZ KONSTRUKTOR
    private val onAction: (User, String) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.MemberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(members[position])
    }

    override fun getItemCount(): Int = members.size

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameText: TextView = itemView.findViewById(R.id.userNameText)
        private val userStatusText: TextView = itemView.findViewById(R.id.userStatusText)
        private val optionsButton: ImageButton = itemView.findViewById(R.id.optionsButton)

        fun bind(user: User) {
            userNameText.text = user.username
            userStatusText.text = if (user.status == "online") "Online" else "Offline"

            // 👇 TERAZ UŻYWAMY currentUserId Z KONSTRUKTORA
            optionsButton.visibility = if (isAdmin && user.id != currentUserId) View.VISIBLE else View.GONE

            optionsButton.setOnClickListener {
                showOptionsMenu(user)
            }
        }

        private fun showOptionsMenu(user: User) {
            val popup = PopupMenu(itemView.context, optionsButton)
            popup.menu.add("Usuń z grupy").setOnMenuItemClickListener {
                onAction(user, "remove")
                true
            }
            popup.menu.add("Mianuj adminem").setOnMenuItemClickListener {
                onAction(user, "make_admin")
                true
            }
            popup.show()
        }
    }
}