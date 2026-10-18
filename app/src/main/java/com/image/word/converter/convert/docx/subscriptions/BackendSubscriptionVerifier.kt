package com.image.word.converter.convert.docx.subscriptions

import com.image.word.converter.convert.docx.backend.AccountRepository
import com.image.word.converter.convert.docx.backend.AccountRepositoryState
import com.image.word.converter.convert.docx.backend.AccountState
import com.image.word.converter.convert.docx.backend.BackendClient
import com.image.word.converter.convert.docx.backend.BackendFailure
import com.image.word.converter.convert.docx.backend.BackendResult
import com.image.word.converter.convert.docx.backend.ConversionAccessMode
import com.image.word.converter.convert.docx.backend.SubscriptionVerificationRequest
import java.time.Instant
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PlayPurchaseState {
    PURCHASED,
    PENDING,
    UNSPECIFIED,
}

data class PlayPurchaseCandidate(
    val productId: String,
    val purchaseToken: String,
    val state: PlayPurchaseState,
)

interface PendingPurchaseStore {
    suspend fun upsert(request: SubscriptionVerificationRequest)
    suspend fun remove(purchaseToken: String)
    suspend fun all(): List<SubscriptionVerificationRequest>
}

interface AuthoritativeAccountGateway {
    suspend fun accept(account: AccountState)
    suspend fun refresh()
}

class RepositoryAccountGateway(
    private val repository: AccountRepository,
) : AuthoritativeAccountGateway {
    override suspend fun accept(account: AccountState) {
        repository.acceptAuthoritativeAccount(account)
    }

    override suspend fun refresh() {
        repository.refresh()
    }
}

sealed interface PurchaseVerificationOutcome {
    val request: SubscriptionVerificationRequest

    data class Verified(
        override val request: SubscriptionVerificationRequest,
        val account: AccountState,
    ) : PurchaseVerificationOutcome

    data class Pending(
        override val request: SubscriptionVerificationRequest,
    ) : PurchaseVerificationOutcome

    data class RetryScheduled(
        override val request: SubscriptionVerificationRequest,
        val failure: BackendFailure,
    ) : PurchaseVerificationOutcome

    data class Rejected(
        override val request: SubscriptionVerificationRequest,
        val failure: BackendFailure,
    ) : PurchaseVerificationOutcome
}

data class PurchaseVerificationBatch(
    val outcomes: List<PurchaseVerificationOutcome>,
) {
    val hasVerifiedPurchase: Boolean
        get() = outcomes.any { it is PurchaseVerificationOutcome.Verified }

    val hasPendingPurchase: Boolean
        get() = outcomes.any { it is PurchaseVerificationOutcome.Pending }

    val hasRetryableFailure: Boolean
        get() = outcomes.any { it is PurchaseVerificationOutcome.RetryScheduled }
}

class BackendSubscriptionVerifier(
    private val backendClient: BackendClient,
    private val accountGateway: AuthoritativeAccountGateway,
    private val pendingStore: PendingPurchaseStore,
    private val maxAutomaticRetries: Int = 2,
    private val retryDelay: suspend (retryNumber: Int) -> Unit = { retryNumber ->
        delay(1_000L shl (retryNumber - 1).coerceAtMost(3))
    },
) {
    private val operationMutex = Mutex()

    suspend fun process(candidates: List<PlayPurchaseCandidate>): PurchaseVerificationBatch =
        operationMutex.withLock {
            val outcomes = candidates
                .filter { it.productId in SUPPORTED_PRODUCT_IDS && it.purchaseToken.isNotBlank() }
                .distinctBy { it.purchaseToken }
                .map { candidate ->
                    val request = SubscriptionVerificationRequest(
                        productId = candidate.productId,
                        purchaseToken = candidate.purchaseToken,
                    )
                    when (candidate.state) {
                        PlayPurchaseState.PURCHASED -> verifyPersisted(request)
                        PlayPurchaseState.PENDING -> PurchaseVerificationOutcome.Pending(request)
                        PlayPurchaseState.UNSPECIFIED -> PurchaseVerificationOutcome.Rejected(
                            request,
                            BackendFailure.InvalidResponse(),
                        )
                    }
                }
            PurchaseVerificationBatch(outcomes)
        }

    suspend fun retryPersisted(): PurchaseVerificationBatch = operationMutex.withLock {
        PurchaseVerificationBatch(pendingStore.all().map { verifyPersisted(it) })
    }

    private suspend fun verifyPersisted(
        request: SubscriptionVerificationRequest,
    ): PurchaseVerificationOutcome {
        pendingStore.upsert(request)
        var retries = 0
        while (true) {
            when (val result = backendClient.verifySubscriptionPurchase(request)) {
                is BackendResult.Success -> {
                    accountGateway.accept(result.value)
                    accountGateway.refresh()
                    pendingStore.remove(request.purchaseToken)
                    return PurchaseVerificationOutcome.Verified(request, result.value)
                }
                is BackendResult.Failure -> {
                    if (!result.failure.isRetryableSubscriptionFailure()) {
                        pendingStore.remove(request.purchaseToken)
                        return PurchaseVerificationOutcome.Rejected(request, result.failure)
                    }
                    if (retries >= maxAutomaticRetries) {
                        return PurchaseVerificationOutcome.RetryScheduled(request, result.failure)
                    }
                    retries += 1
                    retryDelay(retries)
                }
            }
        }
    }

    companion object {
        val SUPPORTED_PRODUCT_IDS = setOf("imagetoword_1w", "imagetoword_1m")
    }
}

private fun BackendFailure.isRetryableSubscriptionFailure(): Boolean = when (this) {
    is BackendFailure.AppCheck,
    is BackendFailure.Authentication,
    is BackendFailure.Network,
    is BackendFailure.InvalidResponse,
    -> true
    is BackendFailure.Server -> error.httpStatus >= 500 || error.code in setOf(
        "SUBSCRIPTION_VERIFICATION_FAILED",
        "INTERNAL_ERROR",
    )
    BackendFailure.IntegrationDisabled -> false
}

data class BackendEntitlementSnapshot(
    val isSubscribed: Boolean,
    val availableCredits: Long?,
    val conversionAccessMode: ConversionAccessMode,
    val freeConversionAvailable: Boolean,
    val freeConversionsRemaining: Int,
    val nextCreditExpiryAt: String?,
    val isAdFree: Boolean,
    val areAdsEligible: Boolean,
    val isLoading: Boolean,
    val isTemporarilyUnavailable: Boolean,
)

object BackendEntitlementPolicy {
    fun from(
        state: AccountRepositoryState,
        now: Instant = Instant.now(),
        lastAuthoritativeAdCheckAt: Instant? = null,
    ): BackendEntitlementSnapshot {
        val account = when (state) {
            is AccountRepositoryState.Ready -> state.account
            is AccountRepositoryState.Loading -> state.previous
            is AccountRepositoryState.Degraded -> state.previous
            AccountRepositoryState.Disabled -> null
        }
        val hasFreshCachedAdFree = account?.conversionAccess?.ads?.adFree == true &&
            lastAuthoritativeAdCheckAt != null &&
            !lastAuthoritativeAdCheckAt.isAfter(now) &&
            Duration.between(lastAuthoritativeAdCheckAt, now) <= AD_FREE_CACHE_TTL &&
            account.credits.nextExpiryAt?.let { expiry ->
                runCatching { Instant.parse(expiry).isAfter(now) }.getOrDefault(false)
            } != false
        val adFree = when (state) {
            is AccountRepositoryState.Ready -> account?.conversionAccess?.ads?.adFree == true
            is AccountRepositoryState.Loading,
            is AccountRepositoryState.Degraded,
            -> hasFreshCachedAdFree
            AccountRepositoryState.Disabled -> false
        }
        return BackendEntitlementSnapshot(
            isSubscribed = account?.subscription?.isEntitledAt(now) == true,
            availableCredits = account?.credits?.available,
            conversionAccessMode = account?.conversionAccess?.mode ?: ConversionAccessMode.NONE,
            freeConversionAvailable = account?.conversionAccess?.freeConversion?.available == true,
            freeConversionsRemaining = account?.conversionAccess?.freeConversion?.remaining ?: 0,
            nextCreditExpiryAt = account?.credits?.nextExpiryAt,
            isAdFree = adFree,
            areAdsEligible = if (adFree) false else when (state) {
                is AccountRepositoryState.Ready -> account?.conversionAccess?.ads?.eligible != false
                else -> true
            },
            isLoading = state is AccountRepositoryState.Loading,
            isTemporarilyUnavailable = state is AccountRepositoryState.Degraded,
        )
    }

    private val AD_FREE_CACHE_TTL: Duration = Duration.ofMinutes(15)

    private fun com.image.word.converter.convert.docx.backend.SubscriptionState.isEntitledAt(
        now: Instant,
    ): Boolean {
        if (status !in setOf("ACTIVE", "GRACE_PERIOD", "CANCELLED")) return false
        val end = runCatching { periodEnd?.let(Instant::parse) }.getOrNull() ?: return false
        return end.isAfter(now)
    }
}
