package com.espressotech.monetization.config

data class AdUnitIds(
    val banner: String,
    val native: String,
    val interstitial: String,
    val frequencyInterstitial: String,
    val appOpen: String,
    val frequencyAppOpen: String,
    val splash: String,
)

data class YandexAdUnitIds(
    val banner: String,
    val native: String,
    val interstitial: String,
    val frequencyInterstitial: String,
    val appOpen: String,
    val frequencyAppOpen: String,
)

data class MonetizationConfig(
    val isDebug: Boolean,
    val remoteConfigPrefix: String,
    val productIds: List<String>,
    val defaultProductId: String,
    val adUnitIds: AdUnitIds,
    val yandexAdUnitIds: YandexAdUnitIds? = null,
    val weekLabel: String,
    val noSubscriptionFoundMessage: String,
    val productLabels: Map<String, String> = emptyMap(),
    val bypassSubscriptionAndAds: Boolean = true,
    val russianMarketUsesAdsInsteadOfSubscription: Boolean = false,
)
