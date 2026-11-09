package com.example.aplikacjatypumessenger.viewmodels

import com.example.aplikacjatypumessenger.models.User

/** Użytkownik na liście z zaznaczaniem (tworzenie grupy, dodawanie członków). */
data class SelectableUserItem(
    val user: User,
    val isSelected: Boolean
)

/** Zaznacza albo odznacza użytkownika; zwraca nowy zbiór, bo stan w ViewModelu jest niemutowalny. */
fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

/** Łączy listę użytkowników z zaznaczeniem w elementy dla adaptera. */
fun List<User>.toSelectableItems(selectedIds: Set<String>): List<SelectableUserItem> =
    map { SelectableUserItem(it, isSelected = it.id in selectedIds) }
