# Monetization portability

The `monetization` module owns subscription state, market access, consent, ad SDK
initialization, provider selection, and all ad format implementations. App code should
only call the stable facades (`BannerAd`, `NativeAdCard`, `InterstitialAdManager`,
`AppOpenAdManager`) or read `SubscriptionViewModel.uiState`.

## Copy to another project

1. Copy the complete `monetization/` folder and include it in `settings.gradle.kts`.
2. Copy the Yandex repository and the Yandex/Meta catalog entries from this project's
   `settings.gradle.kts` and `gradle/libs.versions.toml`.
3. Copy one `AppMonetizationConfig.kt` into each store source set.
4. In the Google config, replace Google ad unit IDs, Yandex ad unit IDs, product IDs,
   and the Remote Config prefix. Keep `russianMarketUsesAdsInsteadOfSubscription = true`
   when Russia should be the free, ad-supported market.
5. Call `Monetization.initialize(...)` and `MarketAccessPolicy.initialize(...)` once,
   before creating `SubscriptionViewModel`.
6. Configure Meta Audience Network as a bidding source for the AdMob units in the
   AdMob console. Placement mapping is server-side; there is no Meta placement ID in code.

Firebase Remote Config uses the single `enableTestAds_<remoteConfigPrefix>` flag for
both Google mediation test IDs and the official Yandex demo placements. It is honored
only by debug builds; release builds always use production IDs.

Google/Yandex code exists only in `src/google`; Huawei code exists only in `src/huawei`.
Do not move provider SDK dependencies to `implementation`, or they will leak into Huawei.

## Region and entitlement behavior

The provider is fixed once per process. `RU` device region or a recognized Russian
timezone selects Yandex. Language does not select a provider. In that market,
`SubscriptionViewModel.uiState.isSubscribed` represents effective premium tool access,
while `isAdEligible` continues to represent the real store entitlement so ads remain
enabled for free users.

For provider testing without changing device settings, pass the debug-only launch
extra `ads_provider_override` with `google` or `yandex`. Release builds ignore it.
