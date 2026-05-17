package com.image.word.converter.convert.docx.subscriptions

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

data class SubscriptionUiState(
    val isLoading: Boolean = true,
    val isPurchasing: Boolean = false,
    val isSubscribed: Boolean = false,
    val products: List<SubscriptionProduct> = emptyList(),
    val selectedProductId: String? = null,
    val alertMessage: String? = null,
    val purchaseSuccessful: Boolean = false,
    val restoreToastMessage: String? = null,
)
