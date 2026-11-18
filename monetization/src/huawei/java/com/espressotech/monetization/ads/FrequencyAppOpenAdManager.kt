package com.espressotech.monetization.ads

import android.app.Activity
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

class FrequencyAppOpenAdManager private constructor() {
    private var isShowingAd = false
    private var isWaitingForLoad = false

    fun load(activity: Activity) {
        InterstitialAdManager.frequencyShared.load(activity)
    }

    fun showIfAvailable(activity: Activity) {
        if (!AdsGate.canRequestAds ||
            !AdsGate.isFrequencyAppOpenEnabled ||
            AdsGate.isSubscribedProvider() ||
            isShowingAd ||
            isWaitingForLoad
        ) {
            Log.d(TAG, "Resume ad skipped by eligibility/state gate")
            return
        }

        if (InterstitialAdManager.frequencyShared.isLoaded) {
            showLoadedAd(activity)
            return
        }

        isWaitingForLoad = true
        Log.d(TAG, "Resume ad was not ready; loading it now")
        InterstitialAdManager.frequencyShared.loadWithCallback(
            activity = activity,
            onLoaded = {
                isWaitingForLoad = false
                val isResumed = (activity as? LifecycleOwner)
                    ?.lifecycle
                    ?.currentState
                    ?.isAtLeast(Lifecycle.State.RESUMED) == true
                if (!activity.isFinishing && !activity.isDestroyed && isResumed) {
                    showLoadedAd(activity)
                } else {
                    Log.d(TAG, "Resume ad loaded after activity left foreground; keeping it for next resume")
                }
            },
            onFailed = {
                isWaitingForLoad = false
                Log.w(TAG, "Resume ad failed to load")
            },
        )
    }

    private fun showLoadedAd(activity: Activity) {
        if (isShowingAd || activity.isFinishing || activity.isDestroyed) return

        isShowingAd = true
        Log.d(TAG, "Showing Petal resume ad")
        InterstitialAdManager.frequencyShared.show(
            activity = activity,
            onClose = {
                isShowingAd = false
                Log.d(TAG, "Petal resume ad closed")
            },
        )
    }

    companion object {
        val shared = FrequencyAppOpenAdManager()
    }
}

private const val TAG = "PetalResumeAd"
