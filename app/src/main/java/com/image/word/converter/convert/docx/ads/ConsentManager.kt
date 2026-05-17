package com.image.word.converter.convert.docx.ads

import android.app.Activity
import android.content.pm.ApplicationInfo
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class ConsentManager private constructor() {
    fun requestConsent(activity: Activity, alwaysShow: Boolean = false, onComplete: () -> Unit = {}) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        if (alwaysShow) {
            consentInformation.reset()
        }

        val isDebugBuild = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
        if (isDebugBuild) {
            val debugSettings = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }
        val params = paramsBuilder.build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (consentInformation.canRequestAds()) {
                    applyConsentState(consentInformation, fallbackToAdsAllowed = false)
                    onComplete()
                    return@requestConsentInfoUpdate
                }

                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    applyConsentState(consentInformation, fallbackToAdsAllowed = false)
                    onComplete()
                }
            },
            {
                applyConsentState(consentInformation, fallbackToAdsAllowed = true)
                onComplete()
            },
        )
    }

    private fun applyConsentState(
        consentInformation: ConsentInformation,
        fallbackToAdsAllowed: Boolean,
    ) {
        val canRequestAds = consentInformation.canRequestAds() || fallbackToAdsAllowed
        AdsGate.canRequestAds = canRequestAds
        AdsGate.isPersonalizedAdsAllowed = { canRequestAds }
    }

    companion object {
        val shared: ConsentManager by lazy { ConsentManager() }
    }
}
