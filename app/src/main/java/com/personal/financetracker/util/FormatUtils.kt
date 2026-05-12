package com.personal.financetracker.util

import java.util.Calendar

object FormatUtils {

    /** Format cents to "KES 1,234.56" */
    fun formatAmount(cents: Int): String {
        return "KES ${String.format("%,.2f", cents / 100.0)}"
    }

    /** Format cents to short form: "1.2K", "3.5M" */
    fun formatAmountShort(cents: Int): String {
        val amount = cents / 100.0
        return when {
            amount >= 1_000_000 -> String.format("%.1fM", amount / 1_000_000)
            amount >= 1_000 -> String.format("%.1fK", amount / 1_000)
            else -> String.format("%.0f", amount)
        }
    }

    /** Get epoch millis for start of a given month */
    fun getMonthStart(year: Int, month: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Get epoch millis for end of a given month */
    fun getMonthEnd(year: Int, month: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }.timeInMillis
    }

    /** Format epoch millis to "Apr 7, 2026" */
    fun formatDate(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}, ${cal.get(Calendar.YEAR)}"
    }

    /** Format epoch millis to "4:30 PM" */
    fun formatTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val hour = cal.get(Calendar.HOUR)
        val minute = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        return "${if (hour == 0) 12 else hour}:${String.format("%02d", minute)} $amPm"
    }

    /** Format a past timestamp as a relative phrase ("just now", "5 min ago", "Apr 7"). */
    fun formatRelative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val delta = now - millis
        if (delta < 0) return formatDate(millis)
        val seconds = delta / 1000
        if (seconds < 60) return "just now"
        val minutes = seconds / 60
        if (minutes < 60) return "$minutes min ago"
        val hours = minutes / 60
        if (hours < 24) return "$hours hr ago"
        val days = hours / 24
        if (days < 7) return "$days day${if (days == 1L) "" else "s"} ago"
        return formatDate(millis)
    }

    /** Get month display name: "April 2026" */
    fun getMonthName(year: Int, month: Int): String {
        val months = arrayOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        return "${months[month]} $year"
    }
}
