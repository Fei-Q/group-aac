package com.example.groupaac.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object TimeUtils {
    fun now(): Long = System.currentTimeMillis()

    fun clockTime(timestamp: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

    fun elapsedSince(timestamp: Long, now: Long = now()): String {
        val minutes = max(0, (now - timestamp) / 60_000)
        return when {
            minutes < 1 -> "now"
            minutes == 1L -> "1 min"
            minutes < 60 -> "${minutes} min"
            else -> "${minutes / 60} hr"
        }
    }

    fun dateLabel(timestamp: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}
