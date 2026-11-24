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
    var yandexBannerId: String = ""
        private set
    var yandexNativeId: String = ""
        private set
    var yandexInterstitialId: String = ""
        private set
    var yandexFrequencyInterstitialId: String = ""
        private set
    var yandexAppOpenId: String = ""
        private set
    var yandexFrequencyAppOpenId: String = ""
        private set

    fun applyIds(
        useGoogleTestAds: Boolean,
        useYandexTestAds: Boolean,
    ) {
        val production = Monetization.config.adUnitIds
        if (useGoogleTestAds) {
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

        val yandex = Monetization.config.yandexAdUnitIds
        if (useYandexTestAds) {
            yandexBannerId = "demo-banner-yandex"
            yandexNativeId = "demo-native-app-yandex"
            yandexInterstitialId = "demo-interstitial-yandex"
            yandexFrequencyInterstitialId = "demo-interstitial-yandex"
            yandexAppOpenId = "demo-appopenad-yandex"
            yandexFrequencyAppOpenId = "demo-appopenad-yandex"
        } else {
            yandexBannerId = yandex?.banner.orEmpty()
            yandexNativeId = yandex?.native.orEmpty()
            yandexInterstitialId = yandex?.interstitial.orEmpty()
            yandexFrequencyInterstitialId = yandex?.frequencyInterstitial.orEmpty()
            yandexAppOpenId = yandex?.appOpen.orEmpty()
            yandexFrequencyAppOpenId = yandex?.frequencyAppOpen.orEmpty()
        }
    }
}
