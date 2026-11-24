package com.espressotech.monetization.ads

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class ConsentManager private constructor() {
    var isPrivacyOptionsRequired by mutableStateOf(false)
        private set

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
                // Always ask UMP to present a newly-required message. canRequestAds can
                // remain true from a previous session while an updated form is pending.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    applyConsentState(activity, consentInformation, fallbackToAdsAllowed = false)
                    onComplete()
                }
            },
            {
                applyConsentState(activity, consentInformation, fallbackToAdsAllowed = false)
                onComplete()
            },
        )
    }

    private fun applyConsentState(
        activity: Activity,
        consentInformation: ConsentInformation,
        fallbackToAdsAllowed: Boolean,
    ) {
        val canRequestAds = consentInformation.canRequestAds() || fallbackToAdsAllowed
        isPrivacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        AdsGate.canRequestAds = canRequestAds
        val allowsPersonalizedAds = AdPersonalizationResolver.allowsPersonalizedAds(
            context = activity,
            consentStatus = consentInformation.consentStatus,
            canRequestAds = canRequestAds,
        )
        AdsGate.isPersonalizedAdsAllowed = { allowsPersonalizedAds }
        AdsGate.notifyConsentStateChanged()
    }

    fun presentPrivacyOptions(activity: Activity) {
        if (!isPrivacyOptionsRequired) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
            applyConsentState(activity, consentInformation, fallbackToAdsAllowed = false)
            InterstitialAdManager.shared.invalidateCachedAd()
            InterstitialAdManager.frequencyShared.invalidateCachedAd()
            AppOpenAdManager.shared.invalidateCachedAd()
            AppOpenAdManager.frequencyShared.invalidateCachedAd()
            AdsInitializer.initialize(activity)
        }
    }

    companion object {
        val shared: ConsentManager by lazy { ConsentManager() }
    }
}
