package com.espressotech.monetization.ads

import android.app.Activity
import com.huawei.hms.ads.HwAds

object AdsInitializer {
    fun initialize(activity: Activity) {
        if (!AdsGate.canRequestAds) return
        HwAds.init(activity)
        InterstitialAdManager.shared.load(activity)
        InterstitialAdManager.frequencyShared.load(activity)
    }
}
