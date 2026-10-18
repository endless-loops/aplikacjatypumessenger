package com.example.aplikacjatypumessenger.viewmodels

import com.example.aplikacjatypumessenger.models.User

/** Użytkownik na liście z zaznaczaniem (tworzenie grupy, dodawanie członków). */
data class SelectableUserItem(
    val user: User,
    val isSelected: Boolean
)

fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

fun List<User>.toSelectableItems(selectedIds: Set<String>): List<SelectableUserItem> =
    map { SelectableUserItem(it, isSelected = it.id in selectedIds) }
