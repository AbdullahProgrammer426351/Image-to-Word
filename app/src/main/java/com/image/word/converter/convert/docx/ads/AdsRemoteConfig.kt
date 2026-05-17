package com.image.word.converter.convert.docx.ads

import android.app.Activity
import android.content.pm.ApplicationInfo
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object AdsRemoteConfig {
    private const val KEY_ENABLE_TEST_ADS = "enableTestAds_ImgToWord_Android"
    private const val KEY_BANNER_ENABLED = "bannerAdStatus_ImgToWord_Android"
    private const val KEY_INTERSTITIAL_ENABLED = "interstitialAdStatus_ImgToWord_Android"
    private const val KEY_FREQUENCY_INTERSTITIAL_ENABLED = "frequencyinterstitialAdStatus_ImgToWord_Android"
    private const val KEY_NATIVE_ENABLED = "nativeAdStatus_ImgToWord_Android"
    private const val KEY_APP_OPEN_ENABLED = "appOpenAdStatus_ImgToWord_Android"
    private const val KEY_FREQUENCY_APP_OPEN_ENABLED = "frequency_appOpenAdStatus_ImgToWord_Android"
    private const val KEY_REWARDED_ENABLED = "rewardedAdStatus_ImgToWord_Android"

    private val remoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }
    private var isDebugBuild: Boolean = false

    fun initialize(activity: Activity, onComplete: () -> Unit = {}) {
        isDebugBuild = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (isDebugBuild) 0 else 3600)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)

        val defaults = mapOf(
            KEY_ENABLE_TEST_ADS to false,
            KEY_BANNER_ENABLED to true,
            KEY_INTERSTITIAL_ENABLED to true,
            KEY_FREQUENCY_INTERSTITIAL_ENABLED to true,
            KEY_NATIVE_ENABLED to true,
            KEY_APP_OPEN_ENABLED to true,
            KEY_FREQUENCY_APP_OPEN_ENABLED to true,
            KEY_REWARDED_ENABLED to true,
        )
        remoteConfig.setDefaultsAsync(defaults)
        applyCurrentValues()

        remoteConfig.fetchAndActivate().addOnCompleteListener(activity) {
            applyCurrentValues()
            onComplete()
        }
    }

    private fun applyCurrentValues() {
        val testAdsEnabledRemote = remoteConfig.getBoolean(KEY_ENABLE_TEST_ADS)
        AdsGate.isAppOpenEnabled = remoteConfig.getBoolean(KEY_APP_OPEN_ENABLED)
        AdsGate.isBannerEnabled = remoteConfig.getBoolean(KEY_BANNER_ENABLED)
        AdsGate.isFrequencyAppOpenEnabled = remoteConfig.getBoolean(KEY_FREQUENCY_APP_OPEN_ENABLED)
        AdsGate.isFrequencyInterstitialEnabled = remoteConfig.getBoolean(KEY_FREQUENCY_INTERSTITIAL_ENABLED)
        AdsGate.isInterstitialEnabled = remoteConfig.getBoolean(KEY_INTERSTITIAL_ENABLED)
        AdsGate.isNativeEnabled = remoteConfig.getBoolean(KEY_NATIVE_ENABLED)
        AdsGate.isRewardedEnabled = remoteConfig.getBoolean(KEY_REWARDED_ENABLED)

        val useTestAds = testAdsEnabledRemote
        AdsId.applyIds(useTestAds)
    }
}
