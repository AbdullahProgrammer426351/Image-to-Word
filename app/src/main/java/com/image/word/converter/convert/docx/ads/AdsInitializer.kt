package com.image.word.converter.convert.docx.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds

object AdsInitializer {
    fun initialize(activity: Activity) {
        if (!AdsGate.canRequestAds) return
        MobileAds.initialize(activity)
        FrequencyAppOpenAdManager.shared.load(activity)
        AppOpenAdManager.shared.loadIfNeeded(activity)
    }
}
