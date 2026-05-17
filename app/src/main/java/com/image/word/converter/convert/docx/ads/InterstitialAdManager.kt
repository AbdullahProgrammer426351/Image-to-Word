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

class InterstitialAdManager {

    private var interstitialAd: InterstitialAd? = null
    private var isShowingAd = false

    fun load(activity: Activity) {
        if (!AdsGate.canRequestAds) return
        if (!AdsGate.isInterstitialEnabled) return
        if (AdsGate.isSubscribedProvider()) return
        if (interstitialAd != null) return

        val request = AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed())
        InterstitialAd.load(
            activity,
            AdsId.interstitialId,
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

        // Safety timeout
        Handler(Looper.getMainLooper()).postDelayed({
            if (!didClose) {
                closeOnce()
            }
        }, 1200)

        if (AdsGate.isSubscribedProvider()) {
            closeOnce()
            return
        }
        if (!AdsGate.canRequestAds || !AdsGate.isInterstitialEnabled || isShowingAd) {
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
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingAd = false
                interstitialAd = null
                closeOnce()
                load(activity)
            }
        }
        ad.show(activity)
    }

    companion object {
        val shared = InterstitialAdManager()
    }
}
