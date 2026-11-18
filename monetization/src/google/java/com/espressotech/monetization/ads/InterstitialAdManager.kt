package com.espressotech.monetization.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdManager(private val adType: AdType) {
    enum class AdType { NORMAL, FREQUENCY }

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private val pendingLoadCallbacks = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    val isLoaded: Boolean get() = interstitialAd != null

    private val adUnitId: String
        get() = when (adType) {
            AdType.NORMAL -> AdsId.interstitialId
            AdType.FREQUENCY -> AdsId.frequencyInterstitialId
        }

    private val isAdEnabled: Boolean
        get() = when (adType) {
            AdType.NORMAL -> AdsGate.isInterstitialEnabled
            AdType.FREQUENCY -> AdsGate.isFrequencyInterstitialEnabled
        }

    fun load(activity: Activity) {
        loadInternal(activity)
    }

    fun loadWithCallback(activity: Activity, onFailed: () -> Unit, onLoaded: () -> Unit) {
        loadInternal(activity, onLoaded, onFailed)
    }

    private fun loadInternal(
        activity: Activity,
        onLoaded: () -> Unit = {},
        onFailed: () -> Unit = {},
    ) {
        if (adUnitId.isBlank() || !AdsGate.canRequestAds || !isAdEnabled || AdsGate.isSubscribedProvider()) {
            onFailed()
            return
        }
        if (interstitialAd != null) {
            onLoaded()
            return
        }
        pendingLoadCallbacks += onLoaded to onFailed
        if (isLoadingAd) return
        isLoadingAd = true

        val request = AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed())
        InterstitialAd.load(
            activity.applicationContext,
            adUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingAd = false
                    interstitialAd = ad
                    val callbacks = pendingLoadCallbacks.toList()
                    pendingLoadCallbacks.clear()
                    callbacks.forEach { it.first() }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    interstitialAd = null
                    val callbacks = pendingLoadCallbacks.toList()
                    pendingLoadCallbacks.clear()
                    callbacks.forEach { it.second() }
                }
            },
        )
    }

    fun show(
        activity: Activity,
        onCompleted: (() -> Unit)? = null,
        onClose: (() -> Unit)? = null,
    ) {
        var didClose = false
        val closeOnce: () -> Unit = {
            if (!didClose) {
                didClose = true
                onClose?.invoke()
            }
        }

        if (AdsGate.isSubscribedProvider()) {
            closeOnce()
            return
        }
        if (!AdsGate.canRequestAds) {
            closeOnce()
            return
        }
        if (!isAdEnabled) {
            closeOnce()
            return
        }
        if (isShowingAd) {
            closeOnce()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            load(activity)
            closeOnce()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingAd = false
                interstitialAd = null
                closeOnce()
                onCompleted?.invoke()
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingAd = false
                interstitialAd = null
                closeOnce()
                load(activity)
            }
        }
        runCatching { ad.show(activity) }
            .onFailure {
                isShowingAd = false
                interstitialAd = null
                closeOnce()
                load(activity)
            }
    }

    companion object {
        val shared = InterstitialAdManager(AdType.NORMAL)
        val frequencyShared = InterstitialAdManager(AdType.FREQUENCY)
    }
}
