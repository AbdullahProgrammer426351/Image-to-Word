package com.espressotech.monetization.ads

import android.app.Activity

class FrequencyAppOpenAdManager private constructor() {
    fun load(activity: Activity) = AppOpenAdManager.frequencyShared.loadIfNeeded(activity)

    fun showIfAvailable(activity: Activity) =
        AppOpenAdManager.frequencyShared.showIfAvailable(activity)

    companion object {
        val shared = FrequencyAppOpenAdManager()
    }
}
