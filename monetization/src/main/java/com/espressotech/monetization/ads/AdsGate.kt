package com.espressotech.monetization.ads

object AdsGate {
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
