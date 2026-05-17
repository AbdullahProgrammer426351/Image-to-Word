package com.image.word.converter.convert.docx.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdManager(private val adType: AdType) {
    enum class AdType { NORMAL, FREQUENCY }

    private var interstitialAd: InterstitialAd? = null
    private var isShowingAd = false

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
        if (adUnitId.isBlank()) return
        if (!AdsGate.canRequestAds) return
        if (!isAdEnabled) return
        if (AdsGate.isSubscribedProvider()) return
        if (interstitialAd != null) return

        val request = AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed())
        InterstitialAd.load(
            activity,
            adUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
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
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    onClose?.invoke()
                } else {
                    Handler(Looper.getMainLooper()).post {
                        onClose?.invoke()
                    }
                }
            }
        }

        // Safety timeout to ensure navigation proceeds even if ad state gets stuck
        Handler(Looper.getMainLooper()).postDelayed({
            if (!didClose) {
                Log.d("InterstitialAdManager", "Safety timeout triggered for onClose")
                closeOnce()
            }
        }, 1200)

        if (AdsGate.isSubscribedProvider()) {
            Log.d("InterstitialAdManager", "User is subscribed, skipping ad")
            closeOnce()
            return
        }
        if (!AdsGate.canRequestAds) {
            Log.d("InterstitialAdManager", "Ads disabled by remote config, skipping ad")
            closeOnce()
            return
        }
        if (!isAdEnabled) {
            Log.d("InterstitialAdManager", "Interstitial type disabled, skipping ad")
            closeOnce()
            return
        }
        if (isShowingAd) {
            Log.d("InterstitialAdManager", "Ad already showing, skipping ad")
            closeOnce()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            Log.d("InterstitialAdManager", "Ad not loaded, triggering load and skipping ad")
            load(activity)
            closeOnce()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("InterstitialAdManager", "Ad dismissed")
                isShowingAd = false
                interstitialAd = null
                closeOnce()
                onCompleted?.invoke()
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d("InterstitialAdManager", "Ad failed to show: ${adError.message}")
                isShowingAd = false
                interstitialAd = null
                closeOnce()
                load(activity)
            }
        }
        Log.d("InterstitialAdManager", "Showing ad...")
        runCatching { ad.show(activity) }
            .onFailure {
                Log.e("InterstitialAdManager", "Exception showing ad", it)
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
