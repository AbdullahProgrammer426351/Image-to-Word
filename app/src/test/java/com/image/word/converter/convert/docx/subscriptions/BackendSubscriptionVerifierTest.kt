package com.image.word.converter.convert.docx.subscriptions

import com.image.word.converter.convert.docx.backend.AccountState
import com.image.word.converter.convert.docx.backend.BackendClient
import com.image.word.converter.convert.docx.backend.BackendError
import com.image.word.converter.convert.docx.backend.BackendFailure
import com.image.word.converter.convert.docx.backend.BackendResult
import com.image.word.converter.convert.docx.backend.BackendUser
import com.image.word.converter.convert.docx.backend.ConversionCredits
import com.image.word.converter.convert.docx.backend.CreditsState
import com.image.word.converter.convert.docx.backend.ImageConversionRequest
import com.image.word.converter.convert.docx.backend.ImageConversionResponse
import com.image.word.converter.convert.docx.backend.SubscriptionState
import com.image.word.converter.convert.docx.backend.SubscriptionVerificationRequest
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSubscriptionVerifierTest {
    @Test
    fun successfulWeeklyVerification_usesServerAccountAndRefreshes() = runTest {
        val account = account(plan = "WEEKLY", available = 595)
        val fixture = fixture(BackendResult.Success(account))

        val batch = fixture.verifier.process(listOf(purchased("imagetoword_1w", "weekly-token")))

        assertTrue(batch.hasVerifiedPurchase)
        assertEquals("imagetoword_1w", fixture.client.requests.single().productId)
        assertEquals("weekly-token", fixture.client.requests.single().purchaseToken)
        assertEquals(account, fixture.gateway.accounts.single())
        assertEquals(1, fixture.gateway.refreshCount)
        assertEquals(595L, fixture.gateway.accounts.single().credits.available)
        assertTrue(fixture.store.all().isEmpty())
    }

    @Test
    fun successfulMonthlyVerification_usesMonthlyServerPlanAndCredits() = runTest {
        val account = account(plan = "MONTHLY", available = 1_295)
        val fixture = fixture(BackendResult.Success(account))

        fixture.verifier.process(listOf(purchased("imagetoword_1m", "monthly-token")))

        assertEquals("imagetoword_1m", fixture.client.requests.single().productId)
        assertEquals("MONTHLY", fixture.gateway.accounts.single().subscription.plan)
        assertEquals(1_295L, fixture.gateway.accounts.single().credits.available)
    }

    @Test
    fun duplicatePurchaseCallback_isCollapsedWithinOnePlayResult() = runTest {
        val fixture = fixture(BackendResult.Success(account()))
        val purchase = purchased("imagetoword_1w", "duplicate-token")

        fixture.verifier.process(listOf(purchase, purchase))

        assertEquals(1, fixture.client.requests.size)
        assertEquals(1, fixture.gateway.accounts.size)
    }

    @Test
    fun duplicateRestore_reusesTokenAndNeverAddsCreditsLocally() = runTest {
        val authoritative = account(available = 500)
        val fixture = fixture(
            BackendResult.Success(authoritative),
            BackendResult.Success(authoritative),
        )
        val purchase = purchased("imagetoword_1w", "restore-token")

        fixture.verifier.process(listOf(purchase))
        fixture.verifier.process(listOf(purchase))

        assertEquals(listOf("restore-token", "restore-token"), fixture.client.requests.map { it.purchaseToken })
        assertEquals(listOf(500L, 500L), fixture.gateway.accounts.map { it.credits.available })
    }

    @Test
    fun backendFailure_persistsAndRetryUsesTheSameProductAndToken() = runTest {
        val fixture = fixture(
            BackendResult.Failure(BackendFailure.Network(IOException("offline"))),
            BackendResult.Success(account()),
            maxAutomaticRetries = 0,
        )
        val request = purchased("imagetoword_1m", "retry-token")

        val first = fixture.verifier.process(listOf(request))
        val second = fixture.verifier.retryPersisted()

        assertTrue(first.hasRetryableFailure)
        assertTrue(second.hasVerifiedPurchase)
        assertEquals(listOf("retry-token", "retry-token"), fixture.client.requests.map { it.purchaseToken })
        assertEquals(listOf("imagetoword_1m", "imagetoword_1m"), fixture.client.requests.map { it.productId })
        assertTrue(fixture.store.all().isEmpty())
    }

    @Test
    fun pendingPurchase_isNotVerifiedOrActivated() = runTest {
        val fixture = fixture()
        val candidate = PlayPurchaseCandidate(
            productId = "imagetoword_1w",
            purchaseToken = "pending-token",
            state = PlayPurchaseState.PENDING,
        )

        val result = fixture.verifier.process(listOf(candidate))

        assertTrue(result.hasPendingPurchase)
        assertTrue(fixture.client.requests.isEmpty())
        assertTrue(fixture.gateway.accounts.isEmpty())
        assertTrue(fixture.store.all().isEmpty())
    }

    @Test
    fun ownershipConflict_isTerminalAndRemovedFromRecovery() = runTest {
        val fixture = fixture(
            BackendResult.Failure(
                BackendFailure.Server(
                    BackendError(
                        code = "PURCHASE_TOKEN_OWNERSHIP_CONFLICT",
                        message = "Conflict",
                        httpStatus = 409,
                    ),
                ),
            ),
        )

        val result = fixture.verifier.process(listOf(purchased("imagetoword_1w", "owned-token")))

        assertTrue(result.outcomes.single() is PurchaseVerificationOutcome.Rejected)
        assertTrue(fixture.store.all().isEmpty())
        assertTrue(fixture.gateway.accounts.isEmpty())
    }

    @Test
    fun authenticationFailure_isRetainedForRecoveryWithoutEntitlement() = runTest {
        val fixture = fixture(
            BackendResult.Failure(BackendFailure.Authentication()),
            maxAutomaticRetries = 0,
        )

        val result = fixture.verifier.process(listOf(purchased("imagetoword_1w", "auth-token")))

        assertTrue(result.hasRetryableFailure)
        assertEquals("auth-token", fixture.store.all().single().purchaseToken)
        assertTrue(fixture.gateway.accounts.isEmpty())
    }

    @Test
    fun playPurchasedState_withoutBackendSuccess_neverGrantsLocalEntitlement() = runTest {
        val fixture = fixture(
            BackendResult.Failure(
                BackendFailure.Server(
                    BackendError(
                        code = "SUBSCRIPTION_VERIFICATION_FAILED",
                        message = "Unavailable",
                        httpStatus = 503,
                    ),
                ),
            ),
            maxAutomaticRetries = 0,
        )

        val result = fixture.verifier.process(listOf(purchased("imagetoword_1m", "unverified")))

        assertFalse(result.hasVerifiedPurchase)
        assertTrue(result.hasRetryableFailure)
        assertTrue(fixture.gateway.accounts.isEmpty())
    }

    @Test
    fun entitlementPolicy_handlesCancelledAndExpiredAuthoritatively() {
        val now = Instant.parse("2026-10-13T12:00:00Z")
        val cancelledButPaid = BackendEntitlementPolicy.from(
            com.image.word.converter.convert.docx.backend.AccountRepositoryState.Ready(
                account(status = "CANCELLED", periodEnd = "2026-10-20T12:00:00Z"),
            ),
            now,
        )
        val expired = BackendEntitlementPolicy.from(
            com.image.word.converter.convert.docx.backend.AccountRepositoryState.Ready(
                account(status = "EXPIRED", periodEnd = "2026-10-12T12:00:00Z"),
            ),
            now,
        )

        assertTrue(cancelledButPaid.isSubscribed)
        assertFalse(expired.isSubscribed)
    }

    private fun purchased(productId: String, token: String) = PlayPurchaseCandidate(
        productId = productId,
        purchaseToken = token,
        state = PlayPurchaseState.PURCHASED,
    )

    private fun account(
        plan: String = "WEEKLY",
        status: String = "ACTIVE",
        periodEnd: String = "2026-12-01T00:00:00Z",
        available: Long = 500,
    ) = AccountState(
        user = BackendUser("user", "Guest", null, "ANONYMOUS"),
        credits = CreditsState(available, 0, null),
        subscription = SubscriptionState(
            plan = plan,
            status = status,
            periodEnd = periodEnd,
            autoRenewing = status == "ACTIVE",
            acknowledgementState = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
        ),
    )

    private fun fixture(
        vararg responses: BackendResult<AccountState>,
        maxAutomaticRetries: Int = 2,
    ): Fixture {
        val client = VerificationBackendClient(ArrayDeque(responses.toList()))
        val store = MemoryPendingPurchaseStore()
        val gateway = RecordingAccountGateway()
        return Fixture(
            client = client,
            store = store,
            gateway = gateway,
            verifier = BackendSubscriptionVerifier(
                backendClient = client,
                accountGateway = gateway,
                pendingStore = store,
                maxAutomaticRetries = maxAutomaticRetries,
                retryDelay = {},
            ),
        )
    }
}

private data class Fixture(
    val client: VerificationBackendClient,
    val store: MemoryPendingPurchaseStore,
    val gateway: RecordingAccountGateway,
    val verifier: BackendSubscriptionVerifier,
)

private class VerificationBackendClient(
    private val responses: ArrayDeque<BackendResult<AccountState>>,
) : BackendClient {
    val requests = mutableListOf<SubscriptionVerificationRequest>()

    override suspend fun bootstrapUser(): BackendResult<AccountState> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun getAccountState(): BackendResult<AccountState> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun imageToWord(
        request: ImageConversionRequest,
    ): BackendResult<ImageConversionResponse> = BackendResult.Success(
        ImageConversionResponse("url", "conversion", ConversionCredits(5, 0)),
    )

    override suspend fun verifySubscriptionPurchase(
        request: SubscriptionVerificationRequest,
    ): BackendResult<AccountState> {
        requests += request
        return responses.removeFirst()
    }
}

private class MemoryPendingPurchaseStore : PendingPurchaseStore {
    private val requests = linkedMapOf<String, SubscriptionVerificationRequest>()

    override suspend fun upsert(request: SubscriptionVerificationRequest) {
        requests[request.purchaseToken] = request
    }

    override suspend fun remove(purchaseToken: String) {
        requests.remove(purchaseToken)
    }

    override suspend fun all(): List<SubscriptionVerificationRequest> = requests.values.toList()
}

private class RecordingAccountGateway : AuthoritativeAccountGateway {
    val accounts = mutableListOf<AccountState>()
    var refreshCount = 0

    override suspend fun accept(account: AccountState) {
        accounts += account
    }

    override suspend fun refresh() {
        refreshCount += 1
    }
}
