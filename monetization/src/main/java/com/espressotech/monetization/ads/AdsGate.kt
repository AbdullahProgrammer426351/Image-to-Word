package com.espressotech.monetization.ads

object AdsGate {
    enum class AdProvider { GOOGLE_MEDIATION, YANDEX, STORE }

    var provider: AdProvider = AdProvider.STORE
        internal set
    var isSdkReady: Boolean = false
        internal set
    var isSubscribedProvider: () -> Boolean = { false }
    var isPersonalizedAdsAllowed: () -> Boolean = { true }
    var canRequestAds: Boolean = true
    var canShowResumeAppOpen: Boolean = false
    var isFullScreenAdShowing: Boolean = false

    var isBannerEnabled: Boolean = true
    var isNativeEnabled: Boolean = true
    var isInterstitialEnabled: Boolean = true
    var isFrequencyInterstitialEnabled: Boolean = true
    var isAppOpenEnabled: Boolean = true
    var isFrequencyAppOpenEnabled: Boolean = true
}
