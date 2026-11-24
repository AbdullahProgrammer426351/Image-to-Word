package com.espressotech.monetization.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AdsGate {
    enum class AdProvider { GOOGLE_MEDIATION, YANDEX, STORE }

    var provider: AdProvider = AdProvider.STORE
        internal set
    var isSdkReady: Boolean by mutableStateOf(false)
        internal set
    var isSubscribedProvider: () -> Boolean = { false }
    var isPersonalizedAdsAllowed: () -> Boolean = { true }
    var canRequestAds: Boolean by mutableStateOf(true)
    var consentRevision: Int by mutableIntStateOf(0)
        private set
    var canShowResumeAppOpen: Boolean = false
    var isFullScreenAdShowing: Boolean = false

    var isBannerEnabled: Boolean = true
    var isNativeEnabled: Boolean = true
    var isInterstitialEnabled: Boolean = true
    var isFrequencyInterstitialEnabled: Boolean = true
    var isAppOpenEnabled: Boolean = true
    var isFrequencyAppOpenEnabled: Boolean = true

    fun notifyConsentStateChanged() {
        consentRevision += 1
    }
}
