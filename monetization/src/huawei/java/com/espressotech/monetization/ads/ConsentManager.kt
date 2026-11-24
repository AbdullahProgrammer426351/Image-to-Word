package com.espressotech.monetization.ads

import android.app.Activity

class ConsentManager private constructor() {
    val isPrivacyOptionsRequired: Boolean get() = false

    fun requestConsent(
        activity: Activity,
        alwaysShow: Boolean = false,
        onComplete: () -> Unit = {},
    ) {
        AdsGate.canRequestAds = true
        AdsGate.isPersonalizedAdsAllowed = { true }
        AdsGate.notifyConsentStateChanged()
        onComplete()
    }

    fun presentPrivacyOptions(activity: Activity) = Unit

    companion object {
        val shared: ConsentManager by lazy { ConsentManager() }
    }
}
