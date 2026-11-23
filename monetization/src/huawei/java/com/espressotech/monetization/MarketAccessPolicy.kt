package com.espressotech.monetization

import android.content.Context
import com.espressotech.monetization.ads.AdsGate

/** Huawei remains completely independent from the Google/Yandex market policy. */
object MarketAccessPolicy {
    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) {
        AdsGate.provider = AdsGate.AdProvider.STORE
    }

    val usesAdsInsteadOfSubscription: Boolean get() = false

    fun hasPremiumAccess(hasStoreSubscription: Boolean): Boolean = hasStoreSubscription

    val shouldShowSubscriptionPaywall: Boolean get() = true
}
