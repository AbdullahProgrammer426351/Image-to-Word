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
    const val DEBUG_PROVIDER_OVERRIDE_EXTRA = "ads_provider_override"

    private var initialized = false

    fun initialize(
        @Suppress("UNUSED_PARAMETER") context: Context,
        debugProviderOverride: String? = null,
    ) {
        if (initialized) return
        initialized = true

        val locale = Locale.getDefault()
        val regionMatch = locale.country.equals("RU", ignoreCase = true)
        val timezone = TimeZone.getDefault().id
        val timezoneMatch = AdRegionDetector.isRussianMarket("", timezone)
        val automaticUseYandex = AdRegionDetector.isRussianMarket(locale.country, timezone)
        val appliedOverride = if (Monetization.config.isDebug) {
            when (debugProviderOverride?.lowercase(Locale.ROOT)) {
                "yandex" -> AdsGate.AdProvider.YANDEX
                "google", "admob" -> AdsGate.AdProvider.GOOGLE_MEDIATION
                else -> null
            }
        } else {
            null
        }
        val useYandex = Monetization.config.yandexAdUnitIds != null &&
            (appliedOverride == AdsGate.AdProvider.YANDEX ||
                appliedOverride == null && automaticUseYandex)

        AdsGate.provider = if (appliedOverride == AdsGate.AdProvider.GOOGLE_MEDIATION) {
            AdsGate.AdProvider.GOOGLE_MEDIATION
        } else if (useYandex) {
            AdsGate.AdProvider.YANDEX
        } else {
            AdsGate.AdProvider.GOOGLE_MEDIATION
        }
        Log.i(
            "MarketAccessPolicy",
            "provider=${AdsGate.provider}, region=${locale.country.ifBlank { "unavailable" }}, " +
                "timezone=$timezone, language=${locale.language.ifBlank { "unavailable" }}, " +
                "regionMatch=$regionMatch, timezoneMatch=$timezoneMatch, " +
                "debugOverride=${appliedOverride?.name ?: "none"}",
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
