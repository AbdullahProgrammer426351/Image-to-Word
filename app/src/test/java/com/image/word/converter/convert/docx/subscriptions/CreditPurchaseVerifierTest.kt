package com.image.word.converter.convert.docx.subscriptions

import com.image.word.converter.convert.docx.backend.AccountState
import com.image.word.converter.convert.docx.backend.AdEntitlementState
import com.image.word.converter.convert.docx.backend.BackendClient
import com.image.word.converter.convert.docx.backend.BackendError
import com.image.word.converter.convert.docx.backend.BackendFailure
import com.image.word.converter.convert.docx.backend.BackendResult
import com.image.word.converter.convert.docx.backend.BackendUser
import com.image.word.converter.convert.docx.backend.ConversionAccessMode
import com.image.word.converter.convert.docx.backend.ConversionAccessState
import com.image.word.converter.convert.docx.backend.CreditPurchaseReceipt
import com.image.word.converter.convert.docx.backend.CreditPurchaseVerificationRequest
import com.image.word.converter.convert.docx.backend.CreditPurchaseVerificationResponse
import com.image.word.converter.convert.docx.backend.CreditsState
import com.image.word.converter.convert.docx.backend.FreeConversionState
import com.image.word.converter.convert.docx.backend.SubscriptionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditPurchaseVerifierTest {
    @Test
    fun successfulPurchase_acceptsServerAccountAndRemovesPersistedToken() = runTest {
        val store = MemoryCreditPurchaseStore()
        val gateway = CreditRecordingAccountGateway()
        val client = CreditVerificationBackendClient(mutableListOf(success(100)))
        val verifier = verifier(client, gateway, store)

        val batch = verifier.process(listOf(candidate("token-1")))

        assertTrue(batch.hasVerifiedPurchase)
        assertEquals(100L, gateway.accepted.single().credits.available)
        assertTrue(store.all().isEmpty())
        assertEquals(listOf("token-1"), client.tokens)
    }

    @Test
    fun duplicateCallbackAndRepeatedSameProductTokens_areIdempotentlySeparated() = runTest {
        val store = MemoryCreditPurchaseStore()
        val client = CreditVerificationBackendClient(mutableListOf(success(100), success(200)))
        val verifier = verifier(client, CreditRecordingAccountGateway(), store)

        val batch = verifier.process(
            listOf(candidate("token-a"), candidate("token-a"), candidate("token-b")),
        )

        assertEquals(2, batch.outcomes.size)
        assertEquals(listOf("token-a", "token-b"), client.tokens)
    }

    @Test
    fun temporaryFailure_survivesRestartAndRetriesSameToken() = runTest {
        val store = MemoryCreditPurchaseStore()
        val temporary = BackendResult.Failure(
            BackendFailure.Server(
                BackendError("PURCHASE_VERIFICATION_UNAVAILABLE", "later", 503),
            ),
        )
        val firstClient = CreditVerificationBackendClient(mutableListOf(temporary))
        val first = verifier(firstClient, CreditRecordingAccountGateway(), store, retries = 0)

        assertTrue(first.process(listOf(candidate("token-restart"))).hasRetryableFailure)
        assertEquals(1, store.all().size)

        val secondClient = CreditVerificationBackendClient(mutableListOf(success(100)))
        val restored = verifier(secondClient, CreditRecordingAccountGateway(), store).retryPersisted()
        assertTrue(restored.hasVerifiedPurchase)
        assertEquals(listOf("token-restart"), secondClient.tokens)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun terminalOwnershipConflict_removesTokenWithoutGrantingLocally() = runTest {
        val store = MemoryCreditPurchaseStore()
        val conflict = BackendResult.Failure(
            BackendFailure.Server(
                BackendError("PURCHASE_TOKEN_OWNERSHIP_CONFLICT", "conflict", 409),
            ),
        )
        val verifier = verifier(
            CreditVerificationBackendClient(mutableListOf(conflict)),
            CreditRecordingAccountGateway(),
            store,
        )

        val batch = verifier.process(listOf(candidate("owned-elsewhere")))

        assertTrue(batch.outcomes.single() is CreditPurchaseVerificationOutcome.Rejected)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun pendingPlayPayment_isNotSentToBackendOrPersisted() = runTest {
        val store = MemoryCreditPurchaseStore()
        val client = CreditVerificationBackendClient(mutableListOf())
        val verifier = verifier(client, CreditRecordingAccountGateway(), store)

        val batch = verifier.process(
            listOf(candidate("pending-token", PlayPurchaseState.PENDING)),
        )

        assertTrue(batch.hasPendingPurchase)
        assertTrue(client.tokens.isEmpty())
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun restore_usesTokenOnlyRecoveryContract() = runTest {
        val client = CreditVerificationBackendClient(mutableListOf(success(100)))
        val verifier = verifier(client, CreditRecordingAccountGateway(), MemoryCreditPurchaseStore())

        val batch = verifier.recover(listOf(candidate("recover-token")))

        assertTrue(batch.hasVerifiedPurchase)
        assertEquals(emptyList<String>(), client.tokens)
        assertEquals(listOf("recover-token"), client.recoveryTokens)
    }

    private fun verifier(
        client: BackendClient,
        gateway: CreditRecordingAccountGateway,
        store: MemoryCreditPurchaseStore,
        retries: Int = 2,
    ) = CreditPurchaseVerifier(
        backendClient = client,
        accountGateway = gateway,
        pendingStore = store,
        enabledProductIds = { setOf(PRODUCT) },
        maxAutomaticRetries = retries,
        retryDelay = {},
    )

    private fun candidate(token: String, state: PlayPurchaseState = PlayPurchaseState.PURCHASED) =
        PlayPurchaseCandidate(PRODUCT, token, state)

    private fun success(credits: Long): BackendResult<CreditPurchaseVerificationResponse> =
        BackendResult.Success(
            CreditPurchaseVerificationResponse(
                CreditPurchaseReceipt(PRODUCT, "CONSUMED", 100),
                account(credits),
            ),
        )

    companion object {
        private const val PRODUCT = "test_pack_100"
    }
}

private class MemoryCreditPurchaseStore : PendingCreditPurchaseStore {
    private val requests = linkedMapOf<String, CreditPurchaseVerificationRequest>()
    override suspend fun upsert(request: CreditPurchaseVerificationRequest) {
        requests[request.purchaseToken] = request
    }
    override suspend fun remove(purchaseToken: String) {
        requests.remove(purchaseToken)
    }
    override suspend fun all() = requests.values.toList()
}

private class CreditRecordingAccountGateway : AuthoritativeAccountGateway {
    val accepted = mutableListOf<AccountState>()
    override suspend fun accept(account: AccountState) {
        accepted += account
    }
    override suspend fun refresh() = Unit
}

private class CreditVerificationBackendClient(
    private val results: MutableList<BackendResult<CreditPurchaseVerificationResponse>>,
) : BackendClient {
    val tokens = mutableListOf<String>()
    val recoveryTokens = mutableListOf<String>()
    override suspend fun bootstrapUser() = BackendResult.Failure(BackendFailure.IntegrationDisabled)
    override suspend fun getAccountState() = BackendResult.Failure(BackendFailure.IntegrationDisabled)
    override suspend fun imageToWord(request: com.image.word.converter.convert.docx.backend.ImageConversionRequest) =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
    override suspend fun verifySubscriptionPurchase(
        request: com.image.word.converter.convert.docx.backend.SubscriptionVerificationRequest,
    ) = BackendResult.Failure(BackendFailure.IntegrationDisabled)
    override suspend fun verifyCreditPurchase(
        request: CreditPurchaseVerificationRequest,
    ): BackendResult<CreditPurchaseVerificationResponse> {
        tokens += request.purchaseToken
        return results.removeAt(0)
    }
    override suspend fun recoverCreditPurchase(
        purchaseToken: String,
    ): BackendResult<CreditPurchaseVerificationResponse> {
        recoveryTokens += purchaseToken
        return results.removeAt(0)
    }
}

private fun account(credits: Long) = AccountState(
    user = BackendUser("user", "Guest", null, "ANONYMOUS"),
    credits = CreditsState(credits, 0, "2026-11-17T00:00:00Z"),
    subscription = SubscriptionState(null, null, null, null, null),
    conversionAccess = ConversionAccessState(
        mode = ConversionAccessMode.CREDIT_BASED,
        canConvert = true,
        freeConversion = FreeConversionState("AVAILABLE", true, 0, 2),
        ads = AdEntitlementState(adFree = true, eligible = false),
    ),
)
