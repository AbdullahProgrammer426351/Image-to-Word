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
            banner = "a0e9j3wpay",
            native = "o1pitxde89",
            interstitial = "w0fol6wjj4",
            frequencyInterstitial = "w0fol6wjj4",
            appOpen = "",
            frequencyAppOpen = "",
            splash = "e6r3r8msnm",
        ),
        weekLabel = context.getString(R.string.week),
        noSubscriptionFoundMessage = context.getString(R.string.str_no_subscription_found_to_restore),
        productLabels = mapOf(
            PRODUCT_WEEKLY to context.getString(R.string.weekly),
            PRODUCT_MONTHLY to context.getString(R.string.monthly),
        ),
        bypassSubscriptionAndAds = false,
    )

    private const val PRODUCT_WEEKLY = "pdfcompress_1w"
    private const val PRODUCT_MONTHLY = "pdfcompress_1m"
}
