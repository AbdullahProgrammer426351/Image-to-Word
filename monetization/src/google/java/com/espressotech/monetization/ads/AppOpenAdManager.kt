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
import java.util.concurrent.TimeUnit

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
    private var loadedAtMillis = 0L

    fun loadIfNeeded(activity: Activity) {
        if (!isFresh()) clearCachedAd()
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
                        loadedAtMillis = System.currentTimeMillis()
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
                        loadedAtMillis = System.currentTimeMillis()
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
        if (!isFresh()) clearCachedAd()
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
        if (!isShowingAd) return
        clearCachedAd()
        setShowing(false)
        closeOnce()
        loadIfNeeded(activity)
    }

    private fun setShowing(showing: Boolean) {
        isShowingAd = showing
        AdsGate.isFullScreenAdShowing = showing
    }

    private fun isFresh(): Boolean {
        if (googleAd == null && yandexAd == null) return true
        return System.currentTimeMillis() - loadedAtMillis < TimeUnit.HOURS.toMillis(1)
    }

    private fun clearCachedAd() {
        googleAd = null
        yandexAd?.setAdEventListener(null)
        yandexAd = null
        loadedAtMillis = 0L
    }

    fun invalidateCachedAd() {
        if (isShowingAd) return
        clearCachedAd()
        yandexLoader = null
        isLoading = false
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
