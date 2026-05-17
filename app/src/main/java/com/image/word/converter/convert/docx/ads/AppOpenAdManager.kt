package com.image.word.converter.convert.docx.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.appopen.AppOpenAd

class AppOpenAdManager private constructor(
    private val adUnitProvider: () -> String,
    private val enabledProvider: () -> Boolean,
) {
    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false

    fun loadIfNeeded(activity: Activity) {
        val adUnitId = adUnitProvider()
        if (adUnitId.isBlank()) return
        if (!AdsGate.canRequestAds) return
        if (!enabledProvider()) return
        if (AdsGate.isSubscribedProvider()) return
        if (appOpenAd != null) return

        val request = AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed())
        AppOpenAd.load(
            activity,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    appOpenAd = null
                }
            },
        )
    }

    fun showIfAvailable(activity: Activity, onClose: (() -> Unit)? = null) {
        if (AdsGate.isSubscribedProvider()) {
            onClose?.invoke()
            return
        }
        if (!AdsGate.canRequestAds) {
            onClose?.invoke()
            return
        }
        if (!enabledProvider()) {
            onClose?.invoke()
            return
        }
        if (isShowingAd) {
            onClose?.invoke()
            return
        }
        val ad = appOpenAd
        if (ad == null) {
            loadIfNeeded(activity)
            onClose?.invoke()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingAd = false
                appOpenAd = null
                onClose?.invoke()
                loadIfNeeded(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingAd = false
                appOpenAd = null
                onClose?.invoke()
                loadIfNeeded(activity)
            }
        }
        ad.show(activity)
    }

    companion object {
        val shared = AppOpenAdManager(
            adUnitProvider = { AdsId.appOpenId },
            enabledProvider = { AdsGate.isAppOpenEnabled },
        )
        val frequencyShared = AppOpenAdManager(
            adUnitProvider = { AdsId.frequencyAppOpenId },
            enabledProvider = { AdsGate.isFrequencyAppOpenEnabled },
        )
    }
}
