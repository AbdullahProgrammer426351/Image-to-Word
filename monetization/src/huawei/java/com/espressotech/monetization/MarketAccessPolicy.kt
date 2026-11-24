package com.espressotech.monetization

import android.content.Context
import com.espressotech.monetization.ads.AdsGate

/** Huawei remains completely independent from the Google/Yandex market policy. */
object MarketAccessPolicy {
    const val DEBUG_PROVIDER_OVERRIDE_EXTRA = "ads_provider_override"

    fun initialize(
        @Suppress("UNUSED_PARAMETER") context: Context,
        @Suppress("UNUSED_PARAMETER") debugProviderOverride: String? = null,
    ) {
        AdsGate.provider = AdsGate.AdProvider.STORE
    }

    val usesAdsInsteadOfSubscription: Boolean get() = false

    fun hasPremiumAccess(hasStoreSubscription: Boolean): Boolean = hasStoreSubscription

    val shouldShowSubscriptionPaywall: Boolean get() = true
}
