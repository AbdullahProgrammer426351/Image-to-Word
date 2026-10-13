package com.image.word.converter.convert.docx.subscriptions

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.backend.BackendAccountStore
import com.image.word.converter.convert.docx.backend.BackendClientStore
import com.image.word.converter.convert.docx.backend.BackendEnvironment
import com.image.word.converter.convert.docx.backend.BackendFailure
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubscriptionViewModel(val app: Application) : AndroidViewModel(app), PurchasesUpdatedListener {
    private val logTag = "SUBSCRIPTION"
    private val environment = BackendEnvironment.current()
    private val usesBackendAuthority = environment.accountIntegrationEnabled
    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState

    private val productIds = listOf(PRODUCT_WEEKLY, PRODUCT_MONTHLY)
    private val pendingStore = PrivatePendingPurchaseStore(app)
    private val backendVerifier = if (usesBackendAuthority) {
        BackendSubscriptionVerifier(
            backendClient = BackendClientStore.client,
            accountGateway = RepositoryAccountGateway(BackendAccountStore.repository),
            pendingStore = pendingStore,
        )
    } else {
        null
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private val productDetailsById = mutableMapOf<String, ProductDetails>()
    private var billingReady = false
    private var resumeQueryRequested = false

    init {
        if (usesBackendAuthority) observeAuthoritativeAccount()
        startConnection()
    }

    fun selectProduct(productId: String) {
        if (productId in productIds) {
            _uiState.update { it.copy(selectedProductId = productId) }
        }
    }

    fun purchase(activity: Activity) {
        val selectedId = _uiState.value.selectedProductId ?: return
        purchaseByProductId(activity, selectedId)
    }

    fun purchaseByProductId(activity: Activity, productId: String) {
        val product = _uiState.value.products.singleOrNull { it.id == productId }
            ?: return showAlert(R.string.subscription_offer_unavailable)
        val productDetails = productDetailsById[productId]
            ?: return showAlert(R.string.subscription_offer_unavailable)
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(product.offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .build()

        _uiState.update {
            it.copy(isPurchasing = true, alertMessage = null, purchaseSuccessful = false)
        }
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.update {
                it.copy(
                    isPurchasing = false,
                    alertMessage = result.debugMessage.ifBlank { app.getString(R.string.purchase_failed) },
                )
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch { queryActivePurchases(fromRestoreAction = true) }
    }

    fun onAppResume() {
        if (billingReady) {
            viewModelScope.launch { recoverThenQuery() }
        } else {
            resumeQueryRequested = true
        }
    }

    fun consumeRestoreToast() {
        _uiState.update { it.copy(restoreToastMessage = null) }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) {
            if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        alertMessage = billingResult.debugMessage.ifBlank {
                            app.getString(R.string.purchase_failed)
                        },
                    )
                }
            } else {
                _uiState.update { it.copy(isPurchasing = false) }
            }
            return
        }
        viewModelScope.launch { handlePurchases(purchases) }
    }

    private fun observeAuthoritativeAccount() {
        viewModelScope.launch {
            BackendAccountStore.repository.state.collectLatest { repositoryState ->
                val entitlement = BackendEntitlementPolicy.from(repositoryState)
                _uiState.update {
                    it.copy(
                        isSubscribed = entitlement.isSubscribed,
                        availableCredits = entitlement.availableCredits,
                        isEntitlementLoading = entitlement.isLoading,
                        isBackendTemporarilyUnavailable = entitlement.isTemporarilyUnavailable,
                    )
                }
            }
        }
    }

    private fun startConnection() {
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        billingReady = true
                        viewModelScope.launch {
                            fetchProducts()
                            recoverThenQuery()
                            if (resumeQueryRequested) {
                                resumeQueryRequested = false
                                recoverThenQuery()
                            }
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                alertMessage = app.getString(
                                    R.string.billing_unavailable,
                                    result.debugMessage,
                                ),
                            )
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    billingReady = false
                }
            },
        )
    }

    private suspend fun recoverThenQuery() {
        if (usesBackendAuthority && canSubmitRealPlayTokensToBackend()) {
            backendVerifier?.retryPersisted()?.let(::applyVerificationResult)
        }
        queryActivePurchases()
    }

    private suspend fun fetchProducts() {
        _uiState.update { it.copy(isLoading = true) }
        val products = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val result = billingClient.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    alertMessage = app.getString(
                        R.string.failed_to_load_subscriptions,
                        result.billingResult.debugMessage,
                    ),
                )
            }
            return
        }

        productDetailsById.clear()
        val mapped = result.productDetailsList.orEmpty().mapNotNull { details ->
            mapToSubscriptionProduct(details)?.also {
                productDetailsById[details.productId] = details
            }
        }
        val productOrder = productIds.withIndex().associate { it.value to it.index }
        val sorted = mapped.sortedBy { productOrder[it.id] ?: Int.MAX_VALUE }
        val defaultId = sorted.firstOrNull { it.id == PRODUCT_MONTHLY }?.id
            ?: sorted.firstOrNull()?.id
        _uiState.update {
            it.copy(
                isLoading = false,
                products = sorted,
                selectedProductId = it.selectedProductId?.takeIf { id -> sorted.any { p -> p.id == id } }
                    ?: defaultId,
                alertMessage = if (sorted.isEmpty()) {
                    app.getString(R.string.subscription_offer_unavailable)
                } else {
                    it.alertMessage
                },
            )
        }
    }

    private suspend fun queryActivePurchases(fromRestoreAction: Boolean = false) {
        if (!billingReady) {
            if (fromRestoreAction) showAlert(R.string.subscription_offer_unavailable)
            return
        }
        val purchasesResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )
        if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            handlePurchases(purchasesResult.purchasesList, fromRestoreAction)
            if (usesBackendAuthority) BackendAccountStore.repository.refresh()
        } else {
            _uiState.update { it.copy(isPurchasing = false) }
        }
    }

    private suspend fun handlePurchases(
        purchases: List<Purchase>,
        fromRestoreAction: Boolean = false,
    ) {
        if (!usesBackendAuthority) {
            handleLegacyPurchases(purchases, fromRestoreAction)
            return
        }

        val candidates = purchases.mapNotNull(::toCandidate)
        if (candidates.any { it.state == PlayPurchaseState.PENDING }) {
            _uiState.update {
                it.copy(
                    isPurchasing = false,
                    alertMessage = app.getString(R.string.subscription_purchase_pending),
                )
            }
        }
        val purchased = candidates.filter { it.state == PlayPurchaseState.PURCHASED }
        if (purchased.isNotEmpty()) {
            if (!canSubmitRealPlayTokensToBackend()) {
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        alertMessage = app.getString(R.string.subscription_emulator_purchase_blocked),
                    )
                }
            } else {
                _uiState.update { it.copy(isPurchasing = true, alertMessage = null) }
                SubscriptionVerificationRetryScheduler.schedule(app)
                backendVerifier?.process(purchased)?.let { batch ->
                    applyVerificationResult(batch)
                    if (!batch.hasRetryableFailure && pendingStore.all().isEmpty()) {
                        SubscriptionVerificationRetryScheduler.cancel(app)
                    }
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    isPurchasing = false,
                    restoreToastMessage = if (fromRestoreAction) {
                        app.getString(R.string.str_no_subscription_found_to_restore)
                    } else {
                        it.restoreToastMessage
                    },
                )
            }
        }
    }

    private suspend fun handleLegacyPurchases(
        purchases: List<Purchase>,
        fromRestoreAction: Boolean,
    ) {
        var hasActive = false
        purchases.distinctBy { it.purchaseToken }.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it in productIds }
            ) {
                hasActive = true
                if (!purchase.isAcknowledged) {
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build(),
                    )
                }
            }
        }
        _uiState.update {
            it.copy(
                isSubscribed = hasActive,
                isPurchasing = false,
                purchaseSuccessful = hasActive,
                alertMessage = if (hasActive) app.getString(R.string.purchase_successful) else it.alertMessage,
                restoreToastMessage = if (fromRestoreAction && !hasActive) {
                    app.getString(R.string.str_no_subscription_found_to_restore)
                } else {
                    it.restoreToastMessage
                },
            )
        }
    }

    private fun toCandidate(purchase: Purchase): PlayPurchaseCandidate? {
        val supportedProducts = purchase.products.filter { it in productIds }.distinct()
        if (supportedProducts.size != 1) return null
        val state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
            else -> PlayPurchaseState.UNSPECIFIED
        }
        Log.d(logTag, "Observed Play purchase product=${supportedProducts.single()}, state=$state")
        return PlayPurchaseCandidate(
            productId = supportedProducts.single(),
            purchaseToken = purchase.purchaseToken,
            state = state,
        )
    }

    private fun applyVerificationResult(batch: PurchaseVerificationBatch) {
        if (batch.hasRetryableFailure) {
            SubscriptionVerificationRetryScheduler.schedule(app)
        }
        val rejected = batch.outcomes.filterIsInstance<PurchaseVerificationOutcome.Rejected>().firstOrNull()
        val verifiedEntitlement = batch.outcomes
            .filterIsInstance<PurchaseVerificationOutcome.Verified>()
            .any { verified ->
                BackendEntitlementPolicy.from(
                    com.image.word.converter.convert.docx.backend.AccountRepositoryState.Ready(
                        verified.account,
                    ),
                ).isSubscribed
            }
        val message = when {
            verifiedEntitlement -> app.getString(R.string.purchase_successful)
            batch.hasVerifiedPurchase -> app.getString(R.string.subscription_not_active)
            batch.hasRetryableFailure -> app.getString(R.string.subscription_verification_retrying)
            batch.hasPendingPurchase -> app.getString(R.string.subscription_purchase_pending)
            rejected?.failure is BackendFailure.Authentication ->
                app.getString(R.string.authentication_failed_message)
            (rejected?.failure as? BackendFailure.Server)?.error?.code ==
                "PURCHASE_TOKEN_OWNERSHIP_CONFLICT" ->
                app.getString(R.string.subscription_ownership_conflict)
            else -> app.getString(R.string.subscription_verification_failed)
        }
        _uiState.update {
            it.copy(
                isPurchasing = false,
                purchaseSuccessful = verifiedEntitlement,
                alertMessage = message,
            )
        }
    }

    private fun canSubmitRealPlayTokensToBackend(): Boolean =
        usesBackendAuthority && !environment.isEmulator

    private fun mapToSubscriptionProduct(details: ProductDetails): SubscriptionProduct? {
        if (details.productId !in productIds) return null
        val offers = details.subscriptionOfferDetails.orEmpty().map { offer ->
            PlaySubscriptionOffer(
                offerToken = offer.offerToken,
                basePlanId = offer.basePlanId,
                offerId = offer.offerId,
                pricingPhases = offer.pricingPhases.pricingPhaseList.map { phase ->
                    PlayPricingPhase(
                        formattedPrice = phase.formattedPrice,
                        priceAmountMicros = phase.priceAmountMicros,
                        priceCurrencyCode = phase.priceCurrencyCode,
                        billingPeriod = phase.billingPeriod,
                    )
                },
            )
        }
        val selected = SubscriptionOfferPolicy.select(details.productId, offers) ?: return null
        val phase = selected.recurringPhase
        val formatter = currencyFormatter(phase.priceCurrencyCode)
        val price = BigDecimal(phase.priceAmountMicros).divide(BigDecimal(1_000_000))
        return SubscriptionProduct(
            id = details.productId,
            title = details.title,
            priceFormatted = phase.formattedPrice,
            billingPeriod = phase.billingPeriod,
            label = app.getString(getLabel(details.productId)),
            offerToken = selected.offer.offerToken,
            weeklyEquivalentText = weeklyEquivalent(price, formatter, phase.billingPeriod),
            priceAmountMicros = phase.priceAmountMicros,
            priceCurrencyCode = phase.priceCurrencyCode,
        )
    }

    private fun currencyFormatter(currencyCode: String): NumberFormat =
        NumberFormat.getCurrencyInstance().apply {
            runCatching { currency = Currency.getInstance(currencyCode) }
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            roundingMode = RoundingMode.DOWN
        }

    @StringRes
    private fun getLabel(id: String): Int = when (id) {
        PRODUCT_MONTHLY -> R.string.monthly
        else -> R.string.weekly
    }

    private fun weeklyEquivalent(
        price: BigDecimal,
        formatter: NumberFormat,
        billingPeriod: String,
    ): String {
        val weekly = when (billingPeriod) {
            "P1W" -> price
            "P1M" -> price.divide(BigDecimal(4), 6, RoundingMode.DOWN)
            else -> price
        }
        return "${formatter.format(weekly)} / ${app.getString(R.string.week)}"
    }

    private fun showAlert(@StringRes message: Int) {
        _uiState.update { it.copy(isPurchasing = false, alertMessage = app.getString(message)) }
    }

    override fun onCleared() {
        billingClient.endConnection()
        super.onCleared()
    }

    companion object {
        const val PRODUCT_WEEKLY = "imagetoword_1w"
        const val PRODUCT_MONTHLY = "imagetoword_1m"
    }
}
