package com.espressotech.monetization.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.yandex.mobile.ads.common.AdRequest as YandexAdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd as YandexInterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

/** Provider-neutral interstitial facade used by app call sites. */
class InterstitialAdManager(private val adType: AdType) {
    enum class AdType { NORMAL, FREQUENCY }

    private var googleAd: InterstitialAd? = null
    private var yandexAd: YandexInterstitialAd? = null
    private var yandexLoader: InterstitialAdLoader? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private val pendingLoadCallbacks = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    val isLoaded: Boolean get() = googleAd != null || yandexAd != null

    private val googleAdUnitId: String
        get() = if (adType == AdType.NORMAL) AdsId.interstitialId else AdsId.frequencyInterstitialId
    private val yandexAdUnitId: String
        get() = if (adType == AdType.NORMAL) AdsId.yandexInterstitialId else AdsId.yandexFrequencyInterstitialId
    private val isAdEnabled: Boolean
        get() = if (adType == AdType.NORMAL) AdsGate.isInterstitialEnabled else AdsGate.isFrequencyInterstitialEnabled

    fun load(activity: Activity) = loadInternal(activity)

    fun loadWithCallback(activity: Activity, onFailed: () -> Unit, onLoaded: () -> Unit) =
        loadInternal(activity, onLoaded, onFailed)

    private fun loadInternal(
        activity: Activity,
        onLoaded: () -> Unit = {},
        onFailed: () -> Unit = {},
    ) {
        val adUnitId = if (AdsGate.provider == AdsGate.AdProvider.YANDEX) yandexAdUnitId else googleAdUnitId
        if (adUnitId.isBlank() || !AdsGate.canRequestAds || !AdsGate.isSdkReady ||
            !isAdEnabled || AdsGate.isSubscribedProvider()
        ) {
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

        if (AdsGate.provider == AdsGate.AdProvider.YANDEX) loadYandex(activity, adUnitId)
        else loadGoogle(activity, adUnitId)
    }

    private fun loadGoogle(activity: Activity, adUnitId: String) {
        InterstitialAd.load(
            activity.applicationContext,
            adUnitId,
            AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed()),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    googleAd = ad
                    finishLoad(true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    googleAd = null
                    finishLoad(false)
                }
            },
        )
    }

    private fun loadYandex(activity: Activity, adUnitId: String) {
        val loader = yandexLoader ?: InterstitialAdLoader(activity.applicationContext).also {
            yandexLoader = it
        }
        loader.loadAd(
            YandexAdRequest.Builder(adUnitId).build(),
            object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: YandexInterstitialAd) {
                    yandexAd = ad
                    finishLoad(true)
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    yandexAd = null
                    finishLoad(false)
                }
            },
        )
    }

    private fun finishLoad(success: Boolean) {
        isLoadingAd = false
        val callbacks = pendingLoadCallbacks.toList()
        pendingLoadCallbacks.clear()
        callbacks.forEach { if (success) it.first() else it.second() }
    }

    fun show(
        activity: Activity,
        onCompleted: (() -> Unit)? = null,
        onClose: (() -> Unit)? = null,
    ) {
        var didContinue = false
        val continueOnce = {
            if (!didContinue) {
                didContinue = true
                onClose?.invoke()
                onCompleted?.invoke()
            }
        }
        if (AdsGate.isSubscribedProvider() || !AdsGate.canRequestAds || !AdsGate.isSdkReady ||
            !isAdEnabled || isShowingAd || AdsGate.isFullScreenAdShowing
        ) {
            continueOnce()
            return
        }
        if (AdsGate.provider == AdsGate.AdProvider.YANDEX) showYandex(activity, continueOnce)
        else showGoogle(activity, continueOnce)
    }

    private fun showGoogle(activity: Activity, continueOnce: () -> Unit) {
        val ad = googleAd ?: run {
            load(activity)
            continueOnce()
            return
        }
        markShowing(true)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finishShow(activity, continueOnce)
            override fun onAdFailedToShowFullScreenContent(adError: AdError) = finishShow(activity, continueOnce)
        }
        runCatching { ad.show(activity) }.onFailure { finishShow(activity, continueOnce) }
    }

    private fun showYandex(activity: Activity, continueOnce: () -> Unit) {
        val ad = yandexAd ?: run {
            load(activity)
            continueOnce()
            return
        }
        markShowing(true)
        ad.setAdEventListener(object : InterstitialAdEventListener {
            override fun onAdShown() = Unit
            override fun onAdFailedToShow(adError: com.yandex.mobile.ads.common.AdError) =
                finishShow(activity, continueOnce)
            override fun onAdDismissed() = finishShow(activity, continueOnce)
            override fun onAdClicked() = Unit
            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        })
        runCatching { ad.show(activity) }.onFailure { finishShow(activity, continueOnce) }
    }

    private fun finishShow(activity: Activity, continueOnce: () -> Unit) {
        if (!isShowingAd) return
        googleAd = null
        yandexAd?.setAdEventListener(null)
        yandexAd = null
        markShowing(false)
        continueOnce()
        load(activity)
    }

    private fun markShowing(showing: Boolean) {
        isShowingAd = showing
        AdsGate.isFullScreenAdShowing = showing
    }

    fun invalidateCachedAd() {
        if (isShowingAd) return
        googleAd = null
        yandexAd?.setAdEventListener(null)
        yandexAd = null
        yandexLoader = null
        isLoadingAd = false
        val callbacks = pendingLoadCallbacks.toList()
        pendingLoadCallbacks.clear()
        callbacks.forEach { it.second() }
    }

    companion object {
        val shared = InterstitialAdManager(AdType.NORMAL)
        val frequencyShared = InterstitialAdManager(AdType.FREQUENCY)
    }
}
