package com.image.word.converter.convert.docx.ads

import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest

object AdRequestHelper {
    fun createRequest(isPersonalizedAdsAllowed: Boolean): AdRequest {
        val builder = AdRequest.Builder()
        if (!isPersonalizedAdsAllowed) {
            val extras = Bundle().apply { putString("npa", "1") }
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }
        return builder.build()
    }
}
