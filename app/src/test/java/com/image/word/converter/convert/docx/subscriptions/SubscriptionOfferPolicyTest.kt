package com.image.word.converter.convert.docx.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionOfferPolicyTest {
    @Test
    fun selectsOnlyOfferMatchingExpectedProductPeriod() {
        val weekly = offer("weekly", "P1W")
        val monthly = offer("monthly", "P1M")

        val selected = SubscriptionOfferPolicy.select("imagetoword_1w", listOf(monthly, weekly))

        assertEquals("weekly", selected?.offer?.offerToken)
        assertEquals("P1W", selected?.recurringPhase?.billingPeriod)
    }

    @Test
    fun multipleMatchingOffers_areRejectedAsAmbiguous() {
        assertNull(
            SubscriptionOfferPolicy.select(
                "imagetoword_1m",
                listOf(offer("base-a", "P1M"), offer("base-b", "P1M")),
            ),
        )
    }

    @Test
    fun yearlyAndUnsupportedProducts_areNeverSelected() {
        assertNull(SubscriptionOfferPolicy.select("imagetoword_1m", listOf(offer("yearly", "P1Y"))))
        assertNull(SubscriptionOfferPolicy.select("imagetoword_1y", listOf(offer("yearly", "P1Y"))))
    }

    private fun offer(token: String, period: String) = PlaySubscriptionOffer(
        offerToken = token,
        basePlanId = "reported-by-play",
        offerId = null,
        pricingPhases = listOf(
            PlayPricingPhase(
                formattedPrice = "$4.99",
                priceAmountMicros = 4_990_000,
                priceCurrencyCode = "USD",
                billingPeriod = period,
            ),
        ),
    )
}
