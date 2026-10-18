package com.example.aplikacjatypumessenger.utils

import android.content.Context
import androidx.annotation.StringRes

/** Tekst dla UI tworzony w ViewModelu bez dostępu do `Context`. */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    fun asString(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.toTypedArray())
        is Raw -> value
    }

    companion object {
        /** Komunikat w formacie "<opis>: <treść wyjątku>". */
        fun error(@StringRes id: Int, error: Throwable): UiText =
            Res(id, listOf(error.localizedMessage ?: error.javaClass.simpleName))
    }
}
