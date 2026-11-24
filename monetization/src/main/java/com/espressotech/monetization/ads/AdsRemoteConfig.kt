package com.espressotech.monetization.ads

import android.app.Activity
import com.espressotech.monetization.Monetization
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object AdsRemoteConfig {
    private val remoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    private fun key(suffix: String): String =
        "${suffix}_${Monetization.config.remoteConfigPrefix}"

    private val appOpenKey get() = key("appOpenAdStatus_ImgToWord_Android")
    private val bannerKey get() = key("bannerAdStatus_ImgToWord_Android")
    private val testAdsKey get() = key("enableTestAds_ImgToWord_Android")
    private val frequencyAppOpenKey get() = key("frequency_appOpenAdStatus_ImgToWord_Android")
    private val frequencyInterstitialKey get() = key("frequencyinterstitialAdStatus_ImgToWord_Android")
    private val interstitialKey get() = key("interstitialAdStatus_ImgToWord_Android")
    private val nativeKey get() = key("nativeAdStatus_ImgToWord_Android")

    fun initialize(activity: Activity, onComplete: () -> Unit = {}) {
        val isDebugBuild = Monetization.config.isDebug
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (isDebugBuild) 0 else 3600)
            .build()
        val defaults = mapOf(
            appOpenKey to true,
            bannerKey to true,
            testAdsKey to true,
            frequencyAppOpenKey to true,
            frequencyInterstitialKey to true,
            interstitialKey to true,
            nativeKey to true,
        )
        remoteConfig.setConfigSettingsAsync(configSettings)
            .continueWithTask { remoteConfig.setDefaultsAsync(defaults) }
            .continueWithTask {
                // Defaults are guaranteed to be installed before they are read or a fetch starts.
                applyCurrentValues()
                remoteConfig.fetchAndActivate()
            }
            .addOnCompleteListener(activity) {
                applyCurrentValues()
                onComplete()
            }
    }

    private fun applyCurrentValues() {
        AdsGate.isAppOpenEnabled = remoteConfig.getBoolean(appOpenKey)
        AdsGate.isBannerEnabled = remoteConfig.getBoolean(bannerKey)
        AdsGate.isFrequencyAppOpenEnabled = remoteConfig.getBoolean(frequencyAppOpenKey)
        AdsGate.isFrequencyInterstitialEnabled = remoteConfig.getBoolean(frequencyInterstitialKey)
        AdsGate.isInterstitialEnabled = remoteConfig.getBoolean(interstitialKey)
        AdsGate.isNativeEnabled = remoteConfig.getBoolean(nativeKey)

        // Never allow test ad IDs in non-debug builds, even if remote flag is true.
        val useTestAds = remoteConfig.getBoolean(testAdsKey) && Monetization.config.isDebug
        AdsId.applyIds(
            useGoogleTestAds = useTestAds,
            useYandexTestAds = useTestAds,
        )
    }
}
