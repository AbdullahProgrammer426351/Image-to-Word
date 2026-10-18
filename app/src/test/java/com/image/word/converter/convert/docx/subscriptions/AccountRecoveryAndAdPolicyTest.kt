package com.image.word.converter.convert.docx.subscriptions

import com.image.word.converter.convert.docx.backend.AccountIdentitySnapshot
import com.image.word.converter.convert.docx.backend.AccountRecoveryPolicy
import com.image.word.converter.convert.docx.backend.AccountRepositoryState
import com.image.word.converter.convert.docx.backend.AccountState
import com.image.word.converter.convert.docx.backend.AdEntitlementState
import com.image.word.converter.convert.docx.backend.BackendFailure
import com.image.word.converter.convert.docx.backend.BackendUser
import com.image.word.converter.convert.docx.backend.ConversionAccessMode
import com.image.word.converter.convert.docx.backend.ConversionAccessState
import com.image.word.converter.convert.docx.backend.CreditsState
import com.image.word.converter.convert.docx.backend.FreeConversionState
import com.image.word.converter.convert.docx.backend.PurchaseRecoveryReadiness
import com.image.word.converter.convert.docx.backend.SubscriptionState
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRecoveryAndAdPolicyTest {
    @Test
    fun backendAdEntitlement_isIndependentFromFreeConversionAndCreditSufficiency() {
        val ready = BackendEntitlementPolicy.from(
            AccountRepositoryState.Ready(account(credits = 1, adFree = true, freeRemaining = 2)),
            now = NOW,
            lastAuthoritativeAdCheckAt = NOW,
        )

        assertTrue(ready.isAdFree)
        assertFalse(ready.areAdsEligible)
        assertEquals(2, ready.freeConversionsRemaining)
        assertEquals(1L, ready.availableCredits)
    }

    @Test
    fun degradedState_retainsPaidAdFreeOnlyForBoundedUnexpiredCache() {
        val account = account(credits = 1, adFree = true, freeRemaining = 0)
        val degraded = AccountRepositoryState.Degraded(
            account,
            BackendFailure.Network(IOException("offline")),
        )

        assertTrue(
            BackendEntitlementPolicy.from(
                degraded,
                now = NOW.plusSeconds(10 * 60),
                lastAuthoritativeAdCheckAt = NOW,
            ).isAdFree,
        )
        assertFalse(
            BackendEntitlementPolicy.from(
                degraded,
                now = NOW.plusSeconds(16 * 60),
                lastAuthoritativeAdCheckAt = NOW,
            ).isAdFree,
        )
    }

    @Test
    fun anonymousIdentity_requiresConfiguredRecoverableSignInBeforePurchase() {
        assertEquals(
            PurchaseRecoveryReadiness.SIGN_IN_REQUIRED,
            AccountRecoveryPolicy.readiness(
                AccountIdentitySnapshot(true, true, setOf("anonymous")),
            ),
        )
        assertEquals(
            PurchaseRecoveryReadiness.RECOVERABLE,
            AccountRecoveryPolicy.readiness(
                AccountIdentitySnapshot(true, false, setOf("password")),
            ),
        )
    }

    private fun account(
        credits: Long,
        adFree: Boolean,
        freeRemaining: Int,
    ) = AccountState(
        BackendUser("user", "Guest", null, "ANONYMOUS"),
        CreditsState(credits, 0, "2026-12-01T00:00:00Z"),
        SubscriptionState(null, null, null, null, null),
        ConversionAccessState(
            mode = if (credits > 0) ConversionAccessMode.CREDIT_BASED else ConversionAccessMode.FREE,
            canConvert = credits > 0 || freeRemaining > 0,
            freeConversion = FreeConversionState(
                status = if (freeRemaining > 0) "AVAILABLE" else "CONSUMED",
                available = freeRemaining > 0,
                successfulConversions = 2 - freeRemaining,
                remaining = freeRemaining,
            ),
            ads = AdEntitlementState(adFree, eligible = !adFree),
        ),
    )

    companion object {
        private val NOW = Instant.parse("2026-10-18T12:00:00Z")
    }
}
