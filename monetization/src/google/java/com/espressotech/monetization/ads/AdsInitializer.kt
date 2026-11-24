package com.espressotech.monetization.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.yandex.mobile.ads.common.YandexAds

object AdsInitializer {
    fun initialize(activity: Activity) {
        if (!AdsGate.canRequestAds) return
        if (AdsGate.provider == AdsGate.AdProvider.YANDEX) {
            YandexAds.setUserConsent(AdsGate.isPersonalizedAdsAllowed())
            YandexAds.setLocationTracking(AdsGate.isPersonalizedAdsAllowed())
        }
        if (AdsGate.isSdkReady) {
            preload(activity)
            return
        }
        when (AdsGate.provider) {
            AdsGate.AdProvider.YANDEX -> {
                YandexAds.initialize(activity.applicationContext) {
                    AdsGate.isSdkReady = true
                    preload(activity)
                }
            }
            AdsGate.AdProvider.GOOGLE_MEDIATION -> MobileAds.initialize(activity) {
                AdsGate.isSdkReady = true
                preload(activity)
            }
            AdsGate.AdProvider.STORE -> Unit
        }
    }

    private fun preload(activity: Activity) {
        InterstitialAdManager.shared.load(activity)
        InterstitialAdManager.frequencyShared.load(activity)
        FrequencyAppOpenAdManager.shared.load(activity)
        AppOpenAdManager.shared.loadIfNeeded(activity)
    }
}
