package com.espressotech.monetization.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.yandex.mobile.ads.appopenad.AppOpenAd as YandexAppOpenAd
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoadListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoader
import com.yandex.mobile.ads.common.AdRequest as YandexAdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData

/** Provider-neutral app-open facade. */
class AppOpenAdManager private constructor(
    private val googleAdUnitProvider: () -> String,
    private val yandexAdUnitProvider: () -> String,
    private val enabledProvider: () -> Boolean,
) {
    private var googleAd: AppOpenAd? = null
    private var yandexAd: YandexAppOpenAd? = null
    private var yandexLoader: AppOpenAdLoader? = null
    private var isLoading = false
    private var isShowingAd = false

    fun loadIfNeeded(activity: Activity) {
        val adUnitId = if (AdsGate.provider == AdsGate.AdProvider.YANDEX) {
            yandexAdUnitProvider()
        } else {
            googleAdUnitProvider()
        }
        if (adUnitId.isBlank() || !AdsGate.canRequestAds || !AdsGate.isSdkReady ||
            !enabledProvider() || AdsGate.isSubscribedProvider() || isLoading ||
            googleAd != null || yandexAd != null
        ) return

        isLoading = true
        if (AdsGate.provider == AdsGate.AdProvider.YANDEX) {
            val loader = yandexLoader ?: AppOpenAdLoader(activity.applicationContext).also {
                yandexLoader = it
            }
            loader.loadAd(
                YandexAdRequest.Builder(adUnitId).build(),
                object : AppOpenAdLoadListener {
                    override fun onAdLoaded(ad: YandexAppOpenAd) {
                        isLoading = false
                        yandexAd = ad
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        isLoading = false
                        yandexAd = null
                    }
                },
            )
        } else {
            AppOpenAd.load(
                activity,
                adUnitId,
                AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed()),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        isLoading = false
                        googleAd = ad
                    }

                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                        isLoading = false
                        googleAd = null
                    }
                },
            )
        }
    }

    fun showIfAvailable(activity: Activity, onClose: (() -> Unit)? = null) {
        var closed = false
        val closeOnce = {
            if (!closed) {
                closed = true
                onClose?.invoke()
            }
        }
        if (AdsGate.isSubscribedProvider() || !AdsGate.canRequestAds || !AdsGate.isSdkReady ||
            !enabledProvider() || isShowingAd || AdsGate.isFullScreenAdShowing
        ) {
            closeOnce()
            return
        }
        if (AdsGate.provider == AdsGate.AdProvider.YANDEX) showYandex(activity, closeOnce)
        else showGoogle(activity, closeOnce)
    }

    private fun showGoogle(activity: Activity, closeOnce: () -> Unit) {
        val ad = googleAd ?: run {
            loadIfNeeded(activity)
            closeOnce()
            return
        }
        setShowing(true)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish(activity, closeOnce)
            override fun onAdFailedToShowFullScreenContent(adError: AdError) = finish(activity, closeOnce)
        }
        runCatching { ad.show(activity) }.onFailure { finish(activity, closeOnce) }
    }

    private fun showYandex(activity: Activity, closeOnce: () -> Unit) {
        val ad = yandexAd ?: run {
            loadIfNeeded(activity)
            closeOnce()
            return
        }
        setShowing(true)
        ad.setAdEventListener(object : AppOpenAdEventListener {
            override fun onAdShown() = Unit
            override fun onAdFailedToShow(adError: com.yandex.mobile.ads.common.AdError) = finish(activity, closeOnce)
            override fun onAdDismissed() = finish(activity, closeOnce)
            override fun onAdClicked() = Unit
            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        })
        runCatching { ad.show(activity) }.onFailure { finish(activity, closeOnce) }
    }

    private fun finish(activity: Activity, closeOnce: () -> Unit) {
        googleAd = null
        yandexAd?.setAdEventListener(null)
        yandexAd = null
        setShowing(false)
        closeOnce()
        loadIfNeeded(activity)
    }

    private fun setShowing(showing: Boolean) {
        isShowingAd = showing
        AdsGate.isFullScreenAdShowing = showing
    }

    companion object {
        val shared = AppOpenAdManager(
            googleAdUnitProvider = { AdsId.appOpenId },
            yandexAdUnitProvider = { AdsId.yandexAppOpenId },
            enabledProvider = { AdsGate.isAppOpenEnabled },
        )
        val frequencyShared = AppOpenAdManager(
            googleAdUnitProvider = { AdsId.frequencyAppOpenId },
            yandexAdUnitProvider = { AdsId.yandexFrequencyAppOpenId },
            enabledProvider = { AdsGate.isFrequencyAppOpenEnabled },
        )
    }
}
