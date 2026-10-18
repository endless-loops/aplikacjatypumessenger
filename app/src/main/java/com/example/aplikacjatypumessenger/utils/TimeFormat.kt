package com.example.aplikacjatypumessenger.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormat {

    fun time(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    /** Dzisiejsze wiadomości jako godzina, starsze jako data. */
    fun chatListTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return ""
        val pattern = if (isSameDay(timestamp, now)) "HH:mm" else "dd.MM"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    fun dateTime(timestamp: Long): String =
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(timestamp))

    private fun isSameDay(a: Long, b: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = a }
        val second = Calendar.getInstance().apply { timeInMillis = b }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
