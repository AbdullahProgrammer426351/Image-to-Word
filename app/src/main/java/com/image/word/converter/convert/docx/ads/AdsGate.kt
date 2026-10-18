package com.image.word.converter.convert.docx.ads

object AdsGate {
    var isAdFreeProvider: () -> Boolean = { false }
    var isCriticalOperationActive: () -> Boolean = { false }
    private var activeCriticalOperations: Int = 0

    @Synchronized
    fun beginCriticalOperation() {
        activeCriticalOperations += 1
    }

    @Synchronized
    fun endCriticalOperation() {
        activeCriticalOperations = (activeCriticalOperations - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun hasActiveCriticalOperation(): Boolean = activeCriticalOperations > 0
    var isPersonalizedAdsAllowed: () -> Boolean = { true }
    var canRequestAds: Boolean = true
    var canShowResumeAppOpen: Boolean = false

    var isBannerEnabled: Boolean = true
    var isNativeEnabled: Boolean = true
    var isInterstitialEnabled: Boolean = true
    var isFrequencyInterstitialEnabled: Boolean = true
    var isAppOpenEnabled: Boolean = true
    var isFrequencyAppOpenEnabled: Boolean = true
    var isRewardedEnabled: Boolean = true
}
