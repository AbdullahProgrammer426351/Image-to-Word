package com.image.word.converter.convert.docx.subscriptions

import com.image.word.converter.convert.docx.backend.SubscriptionVerificationRequest
import com.image.word.converter.convert.docx.backend.CreditPurchaseVerificationRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingPurchaseCodecTest {
    @Test
    fun malformedEntryDoesNotDiscardValidPendingPurchases() {
        val decoded = PendingPurchaseCodec.decode(
            """[{"productId":"imagetoword_1w","purchaseToken":"valid-token"},{"broken":true}]""",
        )

        assertEquals(
            listOf(SubscriptionVerificationRequest("imagetoword_1w", "valid-token")),
            decoded,
        )
    }

    @Test
    fun duplicateTokensAreCollapsedAndUnsupportedProductsIgnored() {
        val request = SubscriptionVerificationRequest("imagetoword_1m", "same-token")
        val encoded = PendingPurchaseCodec.encode(listOf(request, request, SubscriptionVerificationRequest("other", "ignored")))

        assertEquals(listOf(request), PendingPurchaseCodec.decode(encoded))
    }

    @Test
    fun completelyCorruptStorageRecoversAsEmptyWithoutCrashing() {
        val decoded = runCatching { PendingPurchaseCodec.decode("not-json") }.getOrDefault(emptyList())

        assertEquals(emptyList<SubscriptionVerificationRequest>(), decoded)
    }

    @Test
    fun creditPurchaseCodec_preservesValidRecordsAndCollapsesDuplicateTokens() {
        val request = CreditPurchaseVerificationRequest("test_pack_100", "private-token")
        val encoded = PendingCreditPurchaseCodec.encode(listOf(request, request))

        assertEquals(listOf(request), PendingCreditPurchaseCodec.decode(encoded))
        assertEquals(
            listOf(request),
            PendingCreditPurchaseCodec.decode(
                """[{"productId":"test_pack_100","purchaseToken":"private-token"},{"broken":true}]""",
            ),
        )
    }
}
