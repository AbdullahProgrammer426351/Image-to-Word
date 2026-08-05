package com.image.word.converter.convert.docx.ads

object AdsId {
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_FREQUENCY_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_FREQUENCY_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    private const val PROD_BANNER_ID = "ca-app-pub-8218090797316916/1037750078"
    private const val PROD_NATIVE_ID = "ca-app-pub-8218090797316916/7268116504"
    private const val PROD_INTERSTITIAL_ID = "ca-app-pub-8218090797316916/3654654643"
    private const val PROD_FREQUENCY_INTERSTITIAL_ID = "ca-app-pub-8218090797316916/5354749124"
    private const val PROD_APP_OPEN_ID = "ca-app-pub-8218090797316916/6667830799"
    private const val PROD_FREQUENCY_APP_OPEN_ID = "ca-app-pub-8218090797316916/6667830799"
    private const val PROD_REWARDED_ID = "ca-app-pub-8218090797316916/5582348051"

    var bannerId: String = TEST_BANNER_ID
        private set
    var nativeId: String = TEST_NATIVE_ID
        private set
    var interstitialId: String = TEST_INTERSTITIAL_ID
        private set
    var frequencyInterstitialId: String = TEST_FREQUENCY_INTERSTITIAL_ID
        private set
    var appOpenId: String = TEST_APP_OPEN_ID
        private set
    var frequencyAppOpenId: String = TEST_FREQUENCY_APP_OPEN_ID
        private set
    var rewardedId: String = TEST_REWARDED_ID
        private set

    fun applyIds(useTestAds: Boolean) {
        if (useTestAds) {
            bannerId = if (AdsGate.isBannerEnabled) TEST_BANNER_ID else ""
            nativeId = if (AdsGate.isNativeEnabled) TEST_NATIVE_ID else ""
            interstitialId = if (AdsGate.isInterstitialEnabled) TEST_INTERSTITIAL_ID else ""
            frequencyInterstitialId = if (AdsGate.isFrequencyInterstitialEnabled) TEST_FREQUENCY_INTERSTITIAL_ID else ""
            appOpenId = if (AdsGate.isAppOpenEnabled) TEST_APP_OPEN_ID else ""
            frequencyAppOpenId = if (AdsGate.isFrequencyAppOpenEnabled) TEST_FREQUENCY_APP_OPEN_ID else ""
            rewardedId = if (AdsGate.isRewardedEnabled) TEST_REWARDED_ID else ""
            return
        }

        bannerId = if (AdsGate.isBannerEnabled) PROD_BANNER_ID else ""
        nativeId = if (AdsGate.isNativeEnabled) PROD_NATIVE_ID else ""
        interstitialId = if (AdsGate.isInterstitialEnabled) PROD_INTERSTITIAL_ID else ""
        frequencyInterstitialId = if (AdsGate.isFrequencyInterstitialEnabled) PROD_FREQUENCY_INTERSTITIAL_ID else ""
        appOpenId = if (AdsGate.isAppOpenEnabled) PROD_APP_OPEN_ID else ""
        frequencyAppOpenId = if (AdsGate.isFrequencyAppOpenEnabled) PROD_FREQUENCY_APP_OPEN_ID else ""
        rewardedId = if (AdsGate.isRewardedEnabled) PROD_REWARDED_ID else ""
    }
}
