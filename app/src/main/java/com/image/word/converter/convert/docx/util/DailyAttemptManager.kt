package com.image.word.converter.convert.docx.util

import android.content.Context

class DailyAttemptManager(context: Context) {
    private val prefs = context.getSharedPreferences("daily_attempts", Context.MODE_PRIVATE)
    private val countKey = "total_attempt_count"
    val maxAttempts: Int = 1

    val remainingAttempts: Int
        get() {
            val used = prefs.getInt(countKey, 0)
            return (maxAttempts - used).coerceAtLeast(0)
        }

    fun canUse(isSubscribed: Boolean): Boolean {
        if (isSubscribed) return true
        return remainingAttempts > 0
    }

    fun increase() {
        val used = prefs.getInt(countKey, 0)
        prefs.edit().putInt(countKey, used + 1).apply()
    }
}
