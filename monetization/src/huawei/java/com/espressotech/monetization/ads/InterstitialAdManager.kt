package com.espressotech.monetization.ads

import android.app.Activity
import com.huawei.hms.ads.AdListener
import com.huawei.hms.ads.AdParam
import com.huawei.hms.ads.InterstitialAd

class InterstitialAdManager(private val adType: AdType) {
    enum class AdType { NORMAL, FREQUENCY }

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private val pendingLoadCallbacks = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    val isLoaded: Boolean get() = interstitialAd?.isLoaded == true

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

    fun loadWithCallback(
        activity: Activity,
        onFailed: () -> Unit,
        onLoaded: () -> Unit,
    ) {
        if (isLoaded) {
            onLoaded()
            return
        }
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
        if (isLoaded) {
            onLoaded()
            return
        }
        pendingLoadCallbacks += onLoaded to onFailed
        if (isLoadingAd) return
        isLoadingAd = true

        // Construct with the application context so a process-wide manager cannot retain
        // an obsolete Activity across configuration changes. The current Activity is
        // supplied explicitly when the ad is shown.
        val ad = InterstitialAd(activity.applicationContext).apply { adId = adUnitId }
        ad.adListener = object : AdListener() {
            override fun onAdLoaded() {
                isLoadingAd = false
                interstitialAd = ad
                val callbacks = pendingLoadCallbacks.toList()
                pendingLoadCallbacks.clear()
                callbacks.forEach { it.first() }
            }

            override fun onAdFailed(errorCode: Int) {
                isLoadingAd = false
                interstitialAd = null
                val callbacks = pendingLoadCallbacks.toList()
                pendingLoadCallbacks.clear()
                callbacks.forEach { it.second() }
            }
        }
        ad.loadAd(AdParam.Builder().build())
    }

    fun show(
        activity: Activity,
        onCompleted: (() -> Unit)? = null,
        onClose: (() -> Unit)? = null,
    ) {
        var didClose = false
        fun closeOnce() {
            if (!didClose) {
                didClose = true
                onClose?.invoke()
            }
        }

        if (AdsGate.isSubscribedProvider() || !AdsGate.canRequestAds || !isAdEnabled || isShowingAd) {
            closeOnce()
            return
        }
        val ad = interstitialAd
        if (ad?.isLoaded != true) {
            load(activity)
            closeOnce()
            return
        }

        isShowingAd = true
        AdsGate.isFullScreenAdShowing = true
        ad.adListener = object : AdListener() {
            override fun onAdClosed() {
                isShowingAd = false
                AdsGate.isFullScreenAdShowing = false
                interstitialAd = null
                closeOnce()
                onCompleted?.invoke()
                load(activity)
            }

            override fun onAdFailed(errorCode: Int) {
                isShowingAd = false
                AdsGate.isFullScreenAdShowing = false
                interstitialAd = null
                closeOnce()
                load(activity)
            }
        }
        runCatching { ad.show(activity) }.onFailure {
            isShowingAd = false
            AdsGate.isFullScreenAdShowing = false
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
