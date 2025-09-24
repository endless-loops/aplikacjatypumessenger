package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.User
import de.hdodenhof.circleimageview.CircleImageView

class AddMembersAdapter(
    private var users: List<User>,
    private val onSelectionChanged: (User, Boolean) -> Unit
) : RecyclerView.Adapter<AddMembersAdapter.ViewHolder>() {

    private val selectedItems = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rootLayout: ViewGroup = view as ViewGroup // Główny layout to LinearLayout
        val userImageView: CircleImageView = view.findViewById(R.id.userImageView)
        val statusIndicator: CircleImageView = view.findViewById(R.id.statusIndicator)
        val userNameTextView: TextView = view.findViewById(R.id.userNameTextView)
        val userStatusTextView: TextView = view.findViewById(R.id.userStatusTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]

        holder.userNameTextView.text = user.username
        holder.userStatusTextView.text = user.status // lub user.email jeśli masz

        // Ustawienie obrazka profilowego (możesz użyć Glide/Picasso)
        // holder.userImageView.setImageResource(R.drawable.ic_profile_placeholder)

        // Aktualizacja stanu selekcji
        val isSelected = selectedItems.contains(user.id)
        updateSelectionUI(holder, isSelected)

        holder.rootLayout.setOnClickListener {
            val nowSelected = !isSelected
            if (nowSelected) {
                selectedItems.add(user.id)
            } else {
                selectedItems.remove(user.id)
            }
            updateSelectionUI(holder, nowSelected)
            onSelectionChanged(user, nowSelected)
        }
    }

    private fun updateSelectionUI(holder: ViewHolder, isSelected: Boolean) {
        if (isSelected) {
            // Zmiana wyglądu dla zaznaczonego itemu
            holder.rootLayout.setBackgroundResource(R.drawable.bg_item_selected)
            holder.userNameTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorPrimary))
            holder.userStatusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorPrimaryDark))
        } else {
            // Przywrócenie domyślnego wyglądu
            holder.rootLayout.setBackgroundResource(android.R.color.transparent)
            holder.userNameTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
            holder.userStatusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.offline_gray))
        }
    }

    override fun getItemCount() = users.size

    fun updateData(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun getSelectedUsers(): List<User> {
        return users.filter { selectedItems.contains(it.id) }
    }

    fun getSelectedCount(): Int {
        return selectedItems.size
    }

    fun clearSelections() {
        selectedItems.clear()
        notifyDataSetChanged()
    }
}