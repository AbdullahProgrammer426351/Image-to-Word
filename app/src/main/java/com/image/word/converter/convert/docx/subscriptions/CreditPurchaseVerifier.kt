package com.image.word.converter.convert.docx.subscriptions

import com.image.word.converter.convert.docx.backend.AccountState
import com.image.word.converter.convert.docx.backend.BackendClient
import com.image.word.converter.convert.docx.backend.BackendFailure
import com.image.word.converter.convert.docx.backend.BackendResult
import com.image.word.converter.convert.docx.backend.CreditPurchaseVerificationRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PendingCreditPurchaseStore {
    suspend fun upsert(request: CreditPurchaseVerificationRequest)
    suspend fun remove(purchaseToken: String)
    suspend fun all(): List<CreditPurchaseVerificationRequest>
}

sealed interface CreditPurchaseVerificationOutcome {
    val request: CreditPurchaseVerificationRequest

    data class Verified(
        override val request: CreditPurchaseVerificationRequest,
        val account: AccountState,
    ) : CreditPurchaseVerificationOutcome

    data class Pending(
        override val request: CreditPurchaseVerificationRequest,
    ) : CreditPurchaseVerificationOutcome

    data class RetryScheduled(
        override val request: CreditPurchaseVerificationRequest,
        val failure: BackendFailure,
    ) : CreditPurchaseVerificationOutcome

    data class Rejected(
        override val request: CreditPurchaseVerificationRequest,
        val failure: BackendFailure,
    ) : CreditPurchaseVerificationOutcome
}

data class CreditPurchaseVerificationBatch(
    val outcomes: List<CreditPurchaseVerificationOutcome>,
) {
    val hasVerifiedPurchase = outcomes.any { it is CreditPurchaseVerificationOutcome.Verified }
    val hasPendingPurchase = outcomes.any { it is CreditPurchaseVerificationOutcome.Pending }
    val hasRetryableFailure = outcomes.any { it is CreditPurchaseVerificationOutcome.RetryScheduled }
}

class CreditPurchaseVerifier(
    private val backendClient: BackendClient,
    private val accountGateway: AuthoritativeAccountGateway,
    private val pendingStore: PendingCreditPurchaseStore,
    private val enabledProductIds: () -> Set<String>,
    private val maxAutomaticRetries: Int = 2,
    private val retryDelay: suspend (retryNumber: Int) -> Unit = { retryNumber ->
        delay(1_000L shl (retryNumber - 1).coerceAtMost(3))
    },
) {
    private val operationMutex = Mutex()

    suspend fun process(candidates: List<PlayPurchaseCandidate>): CreditPurchaseVerificationBatch =
        processInternal(candidates, recoverByToken = false)

    suspend fun recover(candidates: List<PlayPurchaseCandidate>): CreditPurchaseVerificationBatch =
        processInternal(candidates, recoverByToken = true)

    private suspend fun processInternal(
        candidates: List<PlayPurchaseCandidate>,
        recoverByToken: Boolean,
    ): CreditPurchaseVerificationBatch =
        operationMutex.withLock {
            val enabled = enabledProductIds()
            CreditPurchaseVerificationBatch(
                candidates
                    .filter { it.productId in enabled && it.purchaseToken.isNotBlank() }
                    .distinctBy { it.purchaseToken }
                    .map { candidate ->
                        val request = CreditPurchaseVerificationRequest(
                            productId = candidate.productId,
                            purchaseToken = candidate.purchaseToken,
                        )
                        when (candidate.state) {
                            PlayPurchaseState.PURCHASED -> verifyPersisted(request, recoverByToken)
                            PlayPurchaseState.PENDING ->
                                CreditPurchaseVerificationOutcome.Pending(request)
                            PlayPurchaseState.UNSPECIFIED ->
                                CreditPurchaseVerificationOutcome.Rejected(
                                    request,
                                    BackendFailure.InvalidResponse(),
                                )
                        }
                    },
            )
        }

    suspend fun retryPersisted(): CreditPurchaseVerificationBatch = operationMutex.withLock {
        val enabled = enabledProductIds()
        CreditPurchaseVerificationBatch(
            pendingStore.all().map { request ->
                if (request.productId !in enabled) {
                    pendingStore.remove(request.purchaseToken)
                    CreditPurchaseVerificationOutcome.Rejected(
                        request,
                        BackendFailure.InvalidResponse(),
                    )
                } else {
                    verifyPersisted(request)
                }
            },
        )
    }

    private suspend fun verifyPersisted(
        request: CreditPurchaseVerificationRequest,
        recoverByToken: Boolean = false,
    ): CreditPurchaseVerificationOutcome {
        pendingStore.upsert(request)
        var retries = 0
        while (true) {
            val result = if (recoverByToken) {
                backendClient.recoverCreditPurchase(request.purchaseToken)
            } else {
                backendClient.verifyCreditPurchase(request)
            }
            when (result) {
                is BackendResult.Success -> {
                    accountGateway.accept(result.value.account)
                    accountGateway.refresh()
                    pendingStore.remove(request.purchaseToken)
                    return CreditPurchaseVerificationOutcome.Verified(request, result.value.account)
                }
                is BackendResult.Failure -> {
                    if (!result.failure.isRetryableCreditPurchaseFailure()) {
                        pendingStore.remove(request.purchaseToken)
                        return CreditPurchaseVerificationOutcome.Rejected(request, result.failure)
                    }
                    if (retries >= maxAutomaticRetries) {
                        return CreditPurchaseVerificationOutcome.RetryScheduled(request, result.failure)
                    }
                    retries += 1
                    retryDelay(retries)
                }
            }
        }
    }
}

private fun BackendFailure.isRetryableCreditPurchaseFailure(): Boolean = when (this) {
    is BackendFailure.AppCheck,
    is BackendFailure.Authentication,
    is BackendFailure.Network,
    is BackendFailure.InvalidResponse,
    -> true
    is BackendFailure.Server -> error.httpStatus >= 500 || error.code in setOf(
        "PURCHASE_VERIFICATION_UNAVAILABLE",
        "INTERNAL_ERROR",
    )
    BackendFailure.IntegrationDisabled -> false
}
