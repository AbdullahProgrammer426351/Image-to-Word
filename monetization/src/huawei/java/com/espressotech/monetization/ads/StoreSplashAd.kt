package com.espressotech.monetization.ads

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Huawei splash ads dismiss themselves after their countdown. For the startup
 * experience we need an ad that remains until the user closes it, so Huawei uses
 * the normal Petal interstitial while the Compose splash route stays underneath.
 */
@Composable
fun StoreSplashAd(
    isEntitlementResolved: Boolean,
    isAdEligible: Boolean,
    onFinished: () -> Unit,
) {
    val activity = LocalActivity.current
    var requestStarted by remember { mutableStateOf(false) }
    var adIsShowing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (finished) return
        finished = true
        AdsGate.canShowResumeAppOpen = true
        onFinished()
    }

    LaunchedEffect(activity, isEntitlementResolved, isAdEligible) {
        if (activity == null) {
            finishOnce()
            return@LaunchedEffect
        }
        if (!isEntitlementResolved) {
            Log.d(TAG, "Waiting for subscription entitlement before loading startup ad")
            return@LaunchedEffect
        }
        if (!isAdEligible || !AdsGate.canRequestAds) {
            Log.d(TAG, "Skipping startup ad: isAdEligible=$isAdEligible, canRequestAds=${AdsGate.canRequestAds}")
            finishOnce()
            return@LaunchedEffect
        }
        if (requestStarted) return@LaunchedEffect

        requestStarted = true
        Log.d(TAG, "Loading startup interstitial: id=${AdsId.interstitialId}")
        InterstitialAdManager.shared.loadWithCallback(
            activity = activity,
            onLoaded = {
                if (finished || activity.isFinishing || activity.isDestroyed) {
                    finishOnce()
                    return@loadWithCallback
                }

                adIsShowing = true
                Log.d(TAG, "Showing startup interstitial")
                InterstitialAdManager.shared.show(
                    activity = activity,
                    onClose = {
                        adIsShowing = false
                        Log.d(TAG, "Startup interstitial closed")
                        finishOnce()
                    },
                )
            },
            onFailed = {
                Log.w(TAG, "Startup interstitial failed to load")
                finishOnce()
            },
        )
    }

    LaunchedEffect(requestStarted, adIsShowing) {
        if (!requestStarted || adIsShowing || finished) return@LaunchedEffect
        delay(10_000)
        Log.w(TAG, "Startup interstitial timed out")
        finishOnce()
    }
}

private const val TAG = "PetalStartupAd"
