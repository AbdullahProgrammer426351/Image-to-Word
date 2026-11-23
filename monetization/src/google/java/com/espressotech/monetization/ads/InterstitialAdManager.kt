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
        var didClose = false
        val closeOnce = {
            if (!didClose) {
                didClose = true
                onClose?.invoke()
            }
        }
        if (AdsGate.isSubscribedProvider() || !AdsGate.canRequestAds || !AdsGate.isSdkReady ||
            !isAdEnabled || isShowingAd
        ) {
            closeOnce()
            return
        }
        if (AdsGate.provider == AdsGate.AdProvider.YANDEX) showYandex(activity, closeOnce, onCompleted)
        else showGoogle(activity, closeOnce, onCompleted)
    }

    private fun showGoogle(activity: Activity, closeOnce: () -> Unit, onCompleted: (() -> Unit)?) {
        val ad = googleAd ?: run {
            load(activity)
            closeOnce()
            return
        }
        markShowing(true)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finishShow(activity, closeOnce, onCompleted)
            override fun onAdFailedToShowFullScreenContent(adError: AdError) = finishShow(activity, closeOnce, null)
        }
        runCatching { ad.show(activity) }.onFailure { finishShow(activity, closeOnce, null) }
    }

    private fun showYandex(activity: Activity, closeOnce: () -> Unit, onCompleted: (() -> Unit)?) {
        val ad = yandexAd ?: run {
            load(activity)
            closeOnce()
            return
        }
        markShowing(true)
        ad.setAdEventListener(object : InterstitialAdEventListener {
            override fun onAdShown() = Unit
            override fun onAdFailedToShow(adError: com.yandex.mobile.ads.common.AdError) =
                finishShow(activity, closeOnce, null)
            override fun onAdDismissed() = finishShow(activity, closeOnce, onCompleted)
            override fun onAdClicked() = Unit
            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        })
        runCatching { ad.show(activity) }.onFailure { finishShow(activity, closeOnce, null) }
    }

    private fun finishShow(activity: Activity, closeOnce: () -> Unit, onCompleted: (() -> Unit)?) {
        googleAd = null
        yandexAd?.setAdEventListener(null)
        yandexAd = null
        markShowing(false)
        closeOnce()
        onCompleted?.invoke()
        load(activity)
    }

    private fun markShowing(showing: Boolean) {
        isShowingAd = showing
        AdsGate.isFullScreenAdShowing = showing
    }

    companion object {
        val shared = InterstitialAdManager(AdType.NORMAL)
        val frequencyShared = InterstitialAdManager(AdType.FREQUENCY)
    }
}
