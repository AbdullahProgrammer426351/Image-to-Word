package com.espressotech.monetization.ads

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
fun StoreSplashAd(
    isEntitlementResolved: Boolean,
    isAdEligible: Boolean,
    onFinished: () -> Unit,
) {
    val activity = LocalActivity.current

    LaunchedEffect(activity) {
        delay(5_100)
        if (activity != null) {
            InterstitialAdManager.shared.show(activity = activity, onClose = {
                AdsGate.canShowResumeAppOpen = true
                onFinished()
            })
        } else {
            AdsGate.canShowResumeAppOpen = true
            onFinished()
        }
    }
}
