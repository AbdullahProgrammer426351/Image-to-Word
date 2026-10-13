package com.image.word.converter.convert.docx.subscriptions

data class PlayPricingPhase(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val billingPeriod: String,
)

data class PlaySubscriptionOffer(
    val offerToken: String,
    val basePlanId: String,
    val offerId: String?,
    val pricingPhases: List<PlayPricingPhase>,
)

data class SelectedSubscriptionOffer(
    val offer: PlaySubscriptionOffer,
    val recurringPhase: PlayPricingPhase,
)

object SubscriptionOfferPolicy {
    fun select(
        productId: String,
        offers: List<PlaySubscriptionOffer>,
    ): SelectedSubscriptionOffer? {
        val expectedPeriod = when (productId) {
            SubscriptionViewModel.PRODUCT_WEEKLY -> "P1W"
            SubscriptionViewModel.PRODUCT_MONTHLY -> "P1M"
            else -> return null
        }
        val eligible = offers.mapNotNull { offer ->
            val recurring = offer.pricingPhases.lastOrNull {
                it.billingPeriod == expectedPeriod && it.priceAmountMicros > 0
            } ?: return@mapNotNull null
            SelectedSubscriptionOffer(offer, recurring)
        }
        return eligible.singleOrNull()
    }
}
