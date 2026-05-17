package com.image.word.converter.convert.docx.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.concurrent.TimeUnit

class FrequencyAppOpenAdManager private constructor() {
    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var loadTime: Long = 0L

    fun load(activity: Activity) {
        val adUnitId = AdsId.frequencyAppOpenId
        if (adUnitId.isBlank()) return
        if (!AdsGate.canRequestAds) return
        if (!AdsGate.isFrequencyAppOpenEnabled) return
        if (AdsGate.isSubscribedProvider()) return

        val request = AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed())
        AppOpenAd.load(
            activity,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTime = System.currentTimeMillis()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                }
            },
        )
    }

    fun showIfAvailable(activity: Activity) {
        if (!AdsGate.canRequestAds) return
        if (!AdsGate.isFrequencyAppOpenEnabled) return
        if (AdsGate.isSubscribedProvider()) return
        if (isShowingAd) return
        if (appOpenAd == null || !isAdAvailable()) {
            load(activity)
            return
        }

        isShowingAd = true
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingAd = false
                appOpenAd = null
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingAd = false
                appOpenAd = null
            }
        }
        appOpenAd?.show(activity)
    }

    private fun isAdAvailable(): Boolean {
        val elapsed = System.currentTimeMillis() - loadTime
        return elapsed < TimeUnit.HOURS.toMillis(1)
    }

    companion object {
        val shared = FrequencyAppOpenAdManager()
    }
}
