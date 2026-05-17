package com.image.word.converter.convert.docx.ads

object AdsId {
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    var bannerId: String = TEST_BANNER_ID
        private set
    var interstitialId: String = TEST_INTERSTITIAL_ID
        private set

    fun applyIds(useTestAds: Boolean) {
        if (useTestAds) {
            bannerId = TEST_BANNER_ID
            interstitialId = TEST_INTERSTITIAL_ID
            return
        }
        // Add prod IDs here if needed
    }
}
