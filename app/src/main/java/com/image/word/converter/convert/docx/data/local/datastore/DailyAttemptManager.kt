package com.image.word.converter.convert.docx.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dailyAttemptDataStore by preferencesDataStore(name = "daily_attempts")

class DailyAttemptManager(private val context: Context) {
    private val key = intPreferencesKey("total_attempt_count")
    val maxAttempts: Int = 1

    val remainingAttempts: Flow<Int> = context.dailyAttemptDataStore.data.map { prefs ->
        val used = prefs[key] ?: 0
        (maxAttempts - used).coerceAtLeast(0)
    }

    suspend fun canUse(isSubscribed: Boolean): Boolean {
        if (isSubscribed) return true
        return remainingAttempts.first() > 0
    }

    suspend fun increase() {
        context.dailyAttemptDataStore.edit { prefs ->
            val used = prefs[key] ?: 0
            prefs[key] = used + 1
        }
    }
}
