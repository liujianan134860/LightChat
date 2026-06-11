package com.lightchat.ui.chat

import com.lightchat.model.Message
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TIMESTAMP_GAP_MS = 5 * 60 * 1000L

fun shouldShowTimestamp(previous: Message?, current: Message): Boolean {
    if (previous == null) return true
    return current.createTime - previous.createTime >= TIMESTAMP_GAP_MS
}

fun formatChatTimestamp(timeMillis: Long): String {
    val locale = Locale.CHINA
    val now = Calendar.getInstance(locale)
    val target = Calendar.getInstance(locale).apply { timeInMillis = timeMillis }
    val todayStart = now.clone() as Calendar
    todayStart.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterdayStart = (todayStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val weekStart = (todayStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -6)
    }

    val timePart = SimpleDateFormat("HH:mm", locale).format(target.time)
    return when {
        target.timeInMillis >= todayStart.timeInMillis -> timePart
        target.timeInMillis >= yesterdayStart.timeInMillis -> "昨天 $timePart"
        target.timeInMillis >= weekStart.timeInMillis -> {
            SimpleDateFormat("EEEE HH:mm", locale).format(target.time)
        }
        target.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
            SimpleDateFormat("M月d日 HH:mm", locale).format(target.time)
        }
        else -> {
            SimpleDateFormat("yyyy年M月d日 HH:mm", locale).format(target.time)
        }
    }
}
