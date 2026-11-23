package com.espressotech.monetization

import android.content.Context
import android.util.Log
import com.espressotech.monetization.ads.AdsGate
import java.util.Locale
import java.util.TimeZone

/**
 * Selects one ad provider for the lifetime of the process and exposes the
 * market-level access decision to subscription code. App screens should not
 * perform their own locale or provider checks.
 */
object MarketAccessPolicy {
    private var initialized = false

    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (initialized) return
        initialized = true

        val locale = Locale.getDefault()
        val regionMatch = locale.country.equals("RU", ignoreCase = true)
        val timezone = TimeZone.getDefault().id
        val timezoneMatch = AdRegionDetector.isRussianMarket("", timezone)
        val useYandex = Monetization.config.yandexAdUnitIds != null &&
            AdRegionDetector.isRussianMarket(locale.country, timezone)

        AdsGate.provider = if (useYandex) {
            AdsGate.AdProvider.YANDEX
        } else {
            AdsGate.AdProvider.GOOGLE_MEDIATION
        }
        Log.i(
            "MarketAccessPolicy",
            "provider=${AdsGate.provider}, region=${locale.country.ifBlank { "unavailable" }}, " +
                "timezone=$timezone, language=${locale.language.ifBlank { "unavailable" }}, " +
                "regionMatch=$regionMatch, timezoneMatch=$timezoneMatch",
        )
    }

    val usesAdsInsteadOfSubscription: Boolean
        get() = Monetization.config.russianMarketUsesAdsInsteadOfSubscription &&
            AdsGate.provider == AdsGate.AdProvider.YANDEX

    fun hasPremiumAccess(hasStoreSubscription: Boolean): Boolean =
        usesAdsInsteadOfSubscription || hasStoreSubscription

    val shouldShowSubscriptionPaywall: Boolean
        get() = !usesAdsInsteadOfSubscription
}
