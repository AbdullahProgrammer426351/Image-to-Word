package com.image.word.converter.convert.docx.ads

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun BannerAd(isSubscribed: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adUnitID = AdsId.bannerId

    if (activity == null || adUnitID.isBlank() || isSubscribed || !AdsGate.isBannerEnabled || !AdsGate.canRequestAds)  {
        return 
    }

    val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, 360)

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = {
            AdView(it).apply {
                setAdSize(adSize)
                adUnitId = adUnitID
                loadAd(AdRequestHelper.createRequest(AdsGate.isPersonalizedAdsAllowed()))
            }
        },
    )

}
