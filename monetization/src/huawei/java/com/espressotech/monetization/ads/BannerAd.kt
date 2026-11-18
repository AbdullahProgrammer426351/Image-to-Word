package com.espressotech.monetization.ads

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.huawei.hms.ads.AdParam
import com.huawei.hms.ads.BannerAdSize
import com.huawei.hms.ads.banner.BannerView

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    val adUnitId = AdsId.bannerId
    if (activity == null || adUnitId.isBlank() || AdsGate.isSubscribedProvider() ||
        !AdsGate.isBannerEnabled || !AdsGate.canRequestAds
    ) return

    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = {
            BannerView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                adId = adUnitId
                bannerAdSize = BannerAdSize.BANNER_SIZE_SMART
                loadAd(AdParam.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}
