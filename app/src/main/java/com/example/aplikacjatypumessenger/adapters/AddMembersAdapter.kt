package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.R
import com.example.aplikacjatypumessenger.models.User
import com.google.android.material.card.MaterialCardView

class AddMembersAdapter(
    private var users: List<User>,
    private val onSelectionChanged: (User, Boolean) -> Unit
) : RecyclerView.Adapter<AddMembersAdapter.ViewHolder>() {

    private val selectedItems = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rootView: MaterialCardView = view.findViewById(R.id.card_view)
        val name: TextView = view.findViewById(R.id.tv_name)
        val email: TextView = view.findViewById(R.id.tv_email)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_add_member, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]

        holder.name.text = user.username
        holder.email.text = user.status // możesz tu użyć maila jeśli masz w User

        holder.checkbox.isChecked = selectedItems.contains(user.id)
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedItems.add(user.id) else selectedItems.remove(user.id)
            onSelectionChanged(user, isChecked)
        }

        holder.rootView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
        }
    }

    override fun getItemCount() = users.size

    fun updateData(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun clearSelections() {
        selectedItems.clear()
        notifyDataSetChanged()
    }
}
