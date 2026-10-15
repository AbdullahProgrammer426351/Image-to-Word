package com.image.word.converter.convert.docx.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class DailyAttemptManager(context: Context) {
    private val prefs = context.getSharedPreferences("daily_attempts", Context.MODE_PRIVATE)
    private val countKey = "total_attempt_count"
    val maxAttempts: Int = 2
    private var usedAttempts by mutableIntStateOf(prefs.getInt(countKey, 0))

    val remainingAttempts: Int
        get() = (maxAttempts - usedAttempts).coerceAtLeast(0)

    fun canUse(isSubscribed: Boolean): Boolean {
        if (isSubscribed) return true
        return remainingAttempts > 0
    }

    fun increase() {
        if (usedAttempts >= maxAttempts) return
        usedAttempts += 1
        prefs.edit().putInt(countKey, usedAttempts).apply()
    }
}
