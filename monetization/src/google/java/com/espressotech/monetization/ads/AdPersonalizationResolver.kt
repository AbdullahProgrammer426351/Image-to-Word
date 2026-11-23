package com.espressotech.monetization.ads

import android.content.Context
import com.google.android.ump.ConsentInformation

/** Conservative TCF decision shared by AdMob mediation and Yandex requests. */
internal object AdPersonalizationResolver {
    private const val GOOGLE_VENDOR_ID = 755
    private const val META_ADDITIONAL_CONSENT_ID = 89

    fun allowsPersonalizedAds(
        context: Context,
        consentStatus: Int,
        canRequestAds: Boolean,
    ): Boolean {
        if (!canRequestAds) return false
        val preferences = context.getSharedPreferences(
            "${context.packageName}_preferences",
            Context.MODE_PRIVATE,
        )
        val gdprApplies = when (val value = preferences.all["IABTCF_gdprApplies"]) {
            is Int -> value == 1
            is Boolean -> value
            is String -> value == "1" || value.equals("true", ignoreCase = true)
            else -> null
        }
        if (gdprApplies == false) return true
        if (gdprApplies == null) {
            return consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED
        }
        if (consentStatus != ConsentInformation.ConsentStatus.OBTAINED) return false

        val purposeConsent = preferences.getString("IABTCF_PurposeConsents", null)
        val purposeLegitimateInterest = preferences.getString("IABTCF_PurposeLegitimateInterests", null)
        val vendorConsent = preferences.getString("IABTCF_VendorConsents", null)
        val vendorLegitimateInterest = preferences.getString("IABTCF_VendorLegitimateInterests", null)
        val additionalConsent = preferences.getString("IABTCF_AddtlConsent", null)
        val hasTcString = !preferences.getString("IABTCF_TCString", null).isNullOrBlank()
        val googleConsent = hasBit(vendorConsent, GOOGLE_VENDOR_ID)
        val googleLegitimateInterest = hasBit(vendorLegitimateInterest, GOOGLE_VENDOR_ID)

        return hasTcString &&
            hasBit(purposeConsent, 1) && hasBit(purposeConsent, 3) && hasBit(purposeConsent, 4) &&
            googleConsent &&
            hasConsentOrLegitimateInterest(2, purposeConsent, purposeLegitimateInterest, googleConsent, googleLegitimateInterest) &&
            hasConsentOrLegitimateInterest(7, purposeConsent, purposeLegitimateInterest, googleConsent, googleLegitimateInterest) &&
            hasConsentOrLegitimateInterest(9, purposeConsent, purposeLegitimateInterest, googleConsent, googleLegitimateInterest) &&
            hasConsentOrLegitimateInterest(10, purposeConsent, purposeLegitimateInterest, googleConsent, googleLegitimateInterest) &&
            hasAdditionalConsent(additionalConsent, META_ADDITIONAL_CONSENT_ID)
    }

    private fun hasConsentOrLegitimateInterest(
        purpose: Int,
        purposeConsent: String?,
        purposeLegitimateInterest: String?,
        vendorConsent: Boolean,
        vendorLegitimateInterest: Boolean,
    ): Boolean =
        (hasBit(purposeConsent, purpose) && vendorConsent) ||
            (hasBit(purposeLegitimateInterest, purpose) && vendorLegitimateInterest)

    private fun hasBit(value: String?, oneBasedIndex: Int): Boolean =
        oneBasedIndex > 0 && value?.getOrNull(oneBasedIndex - 1) == '1'

    private fun hasAdditionalConsent(value: String?, id: Int): Boolean =
        value?.substringAfter('~', missingDelimiterValue = "")
            ?.split('.')
            ?.any { it.toIntOrNull() == id } == true
}
