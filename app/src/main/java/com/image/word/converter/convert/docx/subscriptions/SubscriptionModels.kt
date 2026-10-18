package com.image.word.converter.convert.docx.subscriptions

import com.image.word.converter.convert.docx.backend.ConversionAccessMode

data class SubscriptionProduct(
    val id: String,
    val title: String,
    val priceFormatted: String,
    val billingPeriod: String,
    val label: String,
    val offerToken: String,
    val weeklyEquivalentText: String,
    val priceAmountMicros: Long = 0,
    val priceCurrencyCode: String = "",
)

data class CreditProductOffer(
    val id: String,
    val title: String,
    val priceFormatted: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val credits: Long,
    val expiryDays: Int,
)

data class SubscriptionUiState(
    val isLoading: Boolean = true,
    val isPurchasing: Boolean = false,
    val isSubscribed: Boolean = false,
    val products: List<SubscriptionProduct> = emptyList(),
    val creditProducts: List<CreditProductOffer> = emptyList(),
    val selectedProductId: String? = null,
    val selectedCreditProductId: String? = null,
    val alertMessage: String? = null,
    val purchaseSuccessful: Boolean = false,
    val restoreToastMessage: String? = null,
    val availableCredits: Long? = null,
    val conversionAccessMode: ConversionAccessMode = ConversionAccessMode.NONE,
    val freeConversionAvailable: Boolean = false,
    val freeConversionsRemaining: Int = 0,
    val nextCreditExpiryAt: String? = null,
    val isAdFree: Boolean = false,
    val areAdsEligible: Boolean = true,
    val isEntitlementLoading: Boolean = false,
    val isBackendTemporarilyUnavailable: Boolean = false,
)
