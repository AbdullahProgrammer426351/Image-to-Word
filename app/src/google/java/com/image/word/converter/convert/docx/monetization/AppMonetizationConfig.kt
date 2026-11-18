package com.image.word.converter.convert.docx.monetization

import android.content.Context
import com.espressotech.monetization.config.AdUnitIds
import com.espressotech.monetization.config.MonetizationConfig
import com.image.word.converter.convert.docx.BuildConfig
import com.image.word.converter.convert.docx.R

object AppMonetizationConfig {
    fun create(context: Context): MonetizationConfig = MonetizationConfig(
        isDebug = BuildConfig.DEBUG,
        remoteConfigPrefix = "ImgToWord_Android",
        productIds = listOf(PRODUCT_WEEKLY, PRODUCT_MONTHLY),
        defaultProductId = PRODUCT_MONTHLY,
        adUnitIds = AdUnitIds(
            banner = "ca-app-pub-8218090797316916/1037750078",
            native = "ca-app-pub-8218090797316916/7268116504",
            interstitial = "ca-app-pub-8218090797316916/3654654643",
            frequencyInterstitial = "ca-app-pub-8218090797316916/5354749124",
            appOpen = "ca-app-pub-8218090797316916/6667830799",
            frequencyAppOpen = "ca-app-pub-8218090797316916/6667830799",
            splash = "",
        ),
        weekLabel = context.getString(R.string.week),
        noSubscriptionFoundMessage = context.getString(R.string.str_no_subscription_found_to_restore),
        productLabels = mapOf(
            PRODUCT_WEEKLY to context.getString(R.string.weekly),
            PRODUCT_MONTHLY to context.getString(R.string.monthly),
        ),
    )

    private const val PRODUCT_WEEKLY = "imagetoword_1w"
    private const val PRODUCT_MONTHLY = "imagetoword_1m"
}
