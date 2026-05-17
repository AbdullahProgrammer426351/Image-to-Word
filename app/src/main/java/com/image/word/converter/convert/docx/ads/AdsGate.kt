package com.image.word.converter.convert.docx.ads

object AdsGate {
    var isSubscribedProvider: () -> Boolean = { false }
    var isPersonalizedAdsAllowed: () -> Boolean = { true }
    var canRequestAds: Boolean = true

    var isBannerEnabled: Boolean = true
    var isInterstitialEnabled: Boolean = true
}
