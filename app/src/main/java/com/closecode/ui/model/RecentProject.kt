package com.closecode.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class RecentProject(
    val slug: String,
    val name: String,
    val selection: SelectedTemplate,
    val templateName: String,
    val lastOpened: Long,
)

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

fun relativeTime(lastOpened: Long, now: Long = System.currentTimeMillis()): String {
    val elapsed = (now - lastOpened).coerceAtLeast(0)
    return when {
        elapsed < MINUTE -> "Just now"
        elapsed < HOUR -> "${elapsed / MINUTE} min ago"
        elapsed < DAY -> {
            val hours = elapsed / HOUR
            if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }
        elapsed < WEEK -> {
            val days = elapsed / DAY
            if (days == 1L) "Yesterday" else "$days days ago"
        }
        else -> {
            val weeks = elapsed / WEEK
            if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
        }
    }
}