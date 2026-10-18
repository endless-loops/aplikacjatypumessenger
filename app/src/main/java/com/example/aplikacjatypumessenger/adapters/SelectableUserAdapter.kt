package com.example.aplikacjatypumessenger.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacjatypumessenger.databinding.ItemAddMemberBinding
import com.example.aplikacjatypumessenger.viewmodels.SelectableUserItem

/** Lista użytkowników z zaznaczaniem. Stan zaznaczenia trzyma ViewModel. */
class SelectableUserAdapter(
    private val onToggle: (SelectableUserItem) -> Unit
) : ListAdapter<SelectableUserItem, SelectableUserAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAddMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAddMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SelectableUserItem) {
            binding.tvName.text = item.user.username
            binding.tvEmail.text = item.user.email
            binding.checkbox.isChecked = item.isSelected
            // Kliknięcie przekazujemy do ViewModelu; checkbox odświeży się po nowym stanie listy
            binding.checkbox.setOnClickListener { onToggle(item) }
            binding.cardView.setOnClickListener { onToggle(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SelectableUserItem>() {
        override fun areItemsTheSame(oldItem: SelectableUserItem, newItem: SelectableUserItem) = oldItem.user.id == newItem.user.id
        override fun areContentsTheSame(oldItem: SelectableUserItem, newItem: SelectableUserItem) = oldItem == newItem
    }
}
