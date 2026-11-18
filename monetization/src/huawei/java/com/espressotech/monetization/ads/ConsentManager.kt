package com.espressotech.monetization.ads

import android.app.Activity

class ConsentManager private constructor() {
    fun requestConsent(
        activity: Activity,
        alwaysShow: Boolean = false,
        onComplete: () -> Unit = {},
    ) {
        AdsGate.canRequestAds = true
        AdsGate.isPersonalizedAdsAllowed = { true }
        onComplete()
    }

    companion object {
        val shared: ConsentManager by lazy { ConsentManager() }
    }
}
