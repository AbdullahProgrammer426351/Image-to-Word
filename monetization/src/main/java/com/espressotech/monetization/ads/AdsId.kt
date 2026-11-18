package com.espressotech.monetization.ads

import com.espressotech.monetization.Monetization

object AdsId {
    var bannerId: String = ""
        private set
    var nativeId: String = ""
        private set
    var interstitialId: String = ""
        private set
    var frequencyInterstitialId: String = ""
        private set
    var splashId: String = ""
        private set
    var appOpenId: String = ""
        private set
    var frequencyAppOpenId: String = ""
        private set

    fun applyIds(useTestAds: Boolean) {
        val production = Monetization.config.adUnitIds
        if (useTestAds || Monetization.config.isDebug) {
            bannerId = AdIdDefaults.BANNER
            nativeId = AdIdDefaults.NATIVE
            interstitialId = AdIdDefaults.INTERSTITIAL
            frequencyInterstitialId = AdIdDefaults.FREQUENCY_INTERSTITIAL
            splashId = AdIdDefaults.SPLASH
            appOpenId = AdIdDefaults.APP_OPEN
            frequencyAppOpenId = AdIdDefaults.FREQUENCY_APP_OPEN
        } else {
            bannerId = production.banner
            nativeId = production.native
            interstitialId = production.interstitial
            frequencyInterstitialId = production.frequencyInterstitial
            splashId = production.splash
            appOpenId = production.appOpen
            frequencyAppOpenId = production.frequencyAppOpen
        }
    }
}
