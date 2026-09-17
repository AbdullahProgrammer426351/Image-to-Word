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
import java.lang.ref.WeakReference

class InterstitialAdManager(private val adType: AdType) {
    enum class AdType { NORMAL, FREQUENCY }

    private data class PendingShow(
        val activity: WeakReference<Activity>,
        val onComplete: () -> Unit,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var pendingShow: PendingShow? = null
    private var pendingShowTimeout: Runnable? = null

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
        if (adUnitId.isBlank() || !AdsGate.canRequestAds || !isAdEnabled || AdsGate.isSubscribedProvider()) {
            completePendingShowWithoutAd("Ad loading is not allowed")
            return
        }
        if (interstitialAd != null || isLoadingAd) return

        isLoadingAd = true
        Log.d(TAG, "Loading ${adType.name.lowercase()} interstitial...")
        val request = AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed())
        runCatching {
            InterstitialAd.load(
                activity,
                adUnitId,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoadingAd = false
                        interstitialAd = ad
                        Log.d(TAG, "${adType.name.lowercase()} interstitial loaded")
                        showPendingAdIfPossible()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingAd = false
                        interstitialAd = null
                        Log.d(TAG, "Interstitial failed to load: ${error.code} ${error.message}")
                        completePendingShowWithoutAd("Interstitial load failed")
                    }
                },
            )
        }.onFailure { error ->
            isLoadingAd = false
            interstitialAd = null
            Log.e(TAG, "Exception loading interstitial", error)
            completePendingShowWithoutAd("Interstitial load threw an exception")
        }
    }

    fun show(
        activity: Activity,
        onClose: (() -> Unit)? = null,
    ) {
        val completeOnce = onceOnMain(onClose)
        when {
            AdsGate.isSubscribedProvider() -> {
                Log.d(TAG, "User is subscribed, skipping ad")
                completeOnce()
            }
            !AdsGate.canRequestAds -> {
                Log.d(TAG, "Ads disabled by consent, skipping ad")
                completeOnce()
            }
            !isAdEnabled || adUnitId.isBlank() -> {
                Log.d(TAG, "Interstitial type disabled, skipping ad")
                completeOnce()
            }
            isShowingAd -> {
                Log.d(TAG, "Ad already showing; ignoring duplicate show request")
            }
            pendingShow != null -> {
                Log.d(TAG, "Ad show already pending; ignoring duplicate show request")
            }
            interstitialAd != null -> {
                present(activity, interstitialAd!!, completeOnce)
            }
            else -> {
                Log.d(TAG, "Ad is not ready; waiting for the active load before continuing")
                pendingShow = PendingShow(WeakReference(activity), completeOnce)
                schedulePendingShowTimeout()
                load(activity)
            }
        }
    }

    private fun showPendingAdIfPossible() {
        val pending = pendingShow ?: return
        val activity = pending.activity.get()
        val ad = interstitialAd
        if (
            activity == null || activity.isFinishing || activity.isDestroyed ||
            ad == null || AdsGate.isSubscribedProvider() || !AdsGate.canRequestAds || !isAdEnabled
        ) {
            completePendingShowWithoutAd("Pending ad can no longer be presented")
            return
        }

        clearPendingShow()
        present(activity, ad, pending.onComplete)
    }

    private fun present(
        activity: Activity,
        ad: InterstitialAd,
        onComplete: () -> Unit,
    ) {
        cancelPendingShowTimeout()
        interstitialAd = null
        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed")
                isShowingAd = false
                onComplete()
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d(TAG, "Ad failed to show: ${adError.message}")
                isShowingAd = false
                onComplete()
                load(activity)
            }
        }

        Log.d(TAG, "Showing ${adType.name.lowercase()} interstitial...")
        runCatching { ad.show(activity) }
            .onFailure { error ->
                Log.e(TAG, "Exception showing interstitial", error)
                isShowingAd = false
                onComplete()
                load(activity)
            }
    }

    private fun schedulePendingShowTimeout() {
        cancelPendingShowTimeout()
        val timeout = Runnable {
            pendingShowTimeout = null
            completePendingShowWithoutAd("Timed out waiting for interstitial")
        }
        pendingShowTimeout = timeout
        mainHandler.postDelayed(timeout, LOAD_WAIT_TIMEOUT_MS)
    }

    private fun completePendingShowWithoutAd(reason: String) {
        val pending = pendingShow ?: return
        val activity = pending.activity.get()
        Log.d(TAG, "$reason; continuing without ad")
        clearPendingShow()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "Activity is no longer valid; skipping pending completion")
            return
        }
        pending.onComplete()
    }

    private fun clearPendingShow() {
        cancelPendingShowTimeout()
        pendingShow = null
    }

    private fun cancelPendingShowTimeout() {
        pendingShowTimeout?.let(mainHandler::removeCallbacks)
        pendingShowTimeout = null
    }

    private fun onceOnMain(callback: (() -> Unit)?): () -> Unit {
        var completed = false
        return completion@{
            if (completed) return@completion
            completed = true
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback?.invoke()
            } else {
                mainHandler.post { callback?.invoke() }
            }
        }
    }

    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val LOAD_WAIT_TIMEOUT_MS = 10_000L

        val shared = InterstitialAdManager(AdType.NORMAL)
        val frequencyShared = InterstitialAdManager(AdType.FREQUENCY)
    }
}
