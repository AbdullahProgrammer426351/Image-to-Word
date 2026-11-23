package com.espressotech.monetization.ads

import android.app.Activity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.espressotech.monetization.R
import com.yandex.mobile.ads.common.AdRequest as YandexAdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.nativeads.MediaView as YandexMediaView
import com.yandex.mobile.ads.nativeads.NativeAd as YandexNativeAd
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener
import com.yandex.mobile.ads.nativeads.NativeAdLoader
import com.yandex.mobile.ads.nativeads.NativeAdView as YandexNativeAdView
import com.yandex.mobile.ads.nativeads.NativeAdViewBinder

@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adUnitId = if (AdsGate.provider == AdsGate.AdProvider.YANDEX) {
        AdsId.yandexNativeId
    } else {
        AdsId.nativeId
    }

    if (activity == null || adUnitId.isBlank() || AdsGate.isSubscribedProvider() ||
        !AdsGate.isNativeEnabled || !AdsGate.canRequestAds || !AdsGate.isSdkReady
    ) return

    if (AdsGate.provider == AdsGate.AdProvider.YANDEX) {
        YandexNativeAdCard(modifier, adUnitId)
        return
    }

    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(nativeAd) {
        val ad = nativeAd
        onDispose { ad?.destroy() }
    }

    LaunchedEffect(adUnitId) {
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        val request = AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed())
        loader.loadAd(request)
    }

    nativeAd?.let { ad ->
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.native_ad_view, null) as NativeAdView
                populateNativeAdView(ad, view)
                view
            },
            update = { view -> populateNativeAdView(ad, view) },
            onRelease = { it.destroy() },
        )
    }
}

@Composable
private fun YandexNativeAdCard(modifier: Modifier, adUnitId: String) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<YandexNativeAd?>(null) }
    val loader = remember { NativeAdLoader(context.applicationContext) }

    DisposableEffect(loader) {
        onDispose {
            loader.cancelLoading()
            nativeAd?.setNativeAdEventListener(null)
        }
    }

    LaunchedEffect(adUnitId) {
        loader.loadAd(
            YandexAdRequest.Builder(adUnitId).build(),
            object : NativeAdLoadListener {
                override fun onAdLoaded(ad: YandexNativeAd) {
                    nativeAd = ad
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    nativeAd = null
                }
            },
        )
    }

    nativeAd?.let { ad ->
        AndroidView(
            modifier = modifier,
            factory = { ctx -> createYandexNativeView(ctx, ad) },
            update = { bindYandexNativeAd(it, ad) },
        )
    }
}

private fun createYandexNativeView(context: android.content.Context, ad: YandexNativeAd): YandexNativeAdView {
    val root = YandexNativeAdView(context)
    val density = context.resources.displayMetrics.density
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
    }
    val sponsored = TextView(context).apply { text = "Ad" }
    val title = TextView(context).apply { textSize = 16f }
    val body = TextView(context)
    val media = YandexMediaView(context).apply {
        minimumWidth = (300 * density).toInt()
        minimumHeight = (160 * density).toInt()
    }
    val callToAction = TextView(context).apply {
        textSize = 15f
        setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
    }
    content.addView(sponsored)
    content.addView(title)
    content.addView(body)
    content.addView(media, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        (160 * density).toInt(),
    ))
    content.addView(callToAction)
    root.addView(content)
    root.tag = NativeAdViewBinder.Builder(root)
        .setSponsoredView(sponsored)
        .setTitleView(title)
        .setBodyView(body)
        .setMediaView(media)
        .setCallToActionView(callToAction)
        .build()
    bindYandexNativeAd(root, ad)
    return root
}

private fun bindYandexNativeAd(view: YandexNativeAdView, ad: YandexNativeAd) {
    val binder = view.tag as? NativeAdViewBinder ?: return
    ad.bindNativeAd(binder)
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)
    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
    val iconView = adView.findViewById<ImageView>(R.id.ad_icon)
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    val ctaView = adView.findViewById<TextView>(R.id.ad_cta)

    headlineView.text = nativeAd.headline
    adView.headlineView = headlineView

    val advertiser = nativeAd.advertiser
    if (advertiser == null) {
        advertiserView.visibility = android.view.View.GONE
    } else {
        advertiserView.text = advertiser
        advertiserView.visibility = android.view.View.VISIBLE
        adView.advertiserView = advertiserView
    }

    val body = nativeAd.body
    if (body == null) {
        bodyView.visibility = android.view.View.GONE
    } else {
        bodyView.text = body
        bodyView.visibility = android.view.View.VISIBLE
        adView.bodyView = bodyView
    }

    val icon = nativeAd.icon
    if (icon == null) {
        iconView.visibility = android.view.View.GONE
    } else {
        iconView.setImageDrawable(icon.drawable)
        iconView.visibility = android.view.View.VISIBLE
        adView.iconView = iconView
    }

    ctaView.text = nativeAd.callToAction
    adView.callToActionView = ctaView

    adView.mediaView = mediaView
    mediaView.mediaContent = nativeAd.mediaContent

    adView.setNativeAd(nativeAd)
}
