package com.espressotech.monetization.ads

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest as YandexAdRequest
import kotlin.math.roundToInt

/** Provider-neutral banner used by all existing Compose screens. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity ?: return
    if (AdsGate.isSubscribedProvider() || !AdsGate.isBannerEnabled ||
        !AdsGate.canRequestAds || !AdsGate.isSdkReady
    ) return

    key(AdsGate.provider, AdsGate.consentRevision) {
        if (AdsGate.provider == AdsGate.AdProvider.YANDEX) {
            YandexBanner(activity, modifier)
        } else {
            GoogleBanner(activity, modifier)
        }
    }
}

@Composable
private fun GoogleBanner(activity: Activity, modifier: Modifier) {
    val adUnitId = AdsId.bannerId
    if (adUnitId.isBlank()) return
    val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, 360)
    AndroidView(
        modifier = modifier.fillMaxWidth().height(50.dp),
        factory = {
            AdView(it).apply {
                setAdSize(adSize)
                this.adUnitId = adUnitId
                loadAd(AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed()))
            }
        },
        onRelease = { it.destroy() },
    )
}

@Composable
private fun YandexBanner(activity: Activity, modifier: Modifier) {
    val adUnitId = AdsId.yandexBannerId
    if (adUnitId.isBlank()) return
    val widthDp = (activity.resources.displayMetrics.widthPixels /
        activity.resources.displayMetrics.density).roundToInt()
    AndroidView(
        modifier = modifier.fillMaxWidth().height(50.dp),
        factory = {
            BannerAdView(it).apply {
                setAdSize(BannerAdSize.sticky(activity, widthDp))
                loadAd(YandexAdRequest.Builder(adUnitId).build())
            }
        },
        onRelease = { it.destroy() },
    )
}
