package com.image.word.converter.convert.docx.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SubscriptionTriggerManager {
    private val _showLimitDialog = MutableStateFlow(false)
    val showLimitDialog: StateFlow<Boolean> = _showLimitDialog

    fun showLimit() {
        _showLimitDialog.value = true
    }

    fun hideLimit() {
        _showLimitDialog.value = false
    }
}
