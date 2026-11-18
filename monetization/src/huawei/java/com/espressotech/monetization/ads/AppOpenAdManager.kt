package com.espressotech.monetization.ads

import android.app.Activity

class AppOpenAdManager private constructor() {
    fun loadIfNeeded(activity: Activity) = Unit
    fun showIfAvailable(activity: Activity, onClose: (() -> Unit)? = null) = onClose?.invoke()

    companion object {
        val shared = AppOpenAdManager()
        val frequencyShared = AppOpenAdManager()
    }
}
