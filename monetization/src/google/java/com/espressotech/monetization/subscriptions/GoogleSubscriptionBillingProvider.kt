package com.espressotech.monetization.subscriptions

import android.app.Activity
import android.app.Application
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
import com.espressotech.monetization.Monetization
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class GoogleSubscriptionBillingProvider(
    private val app: Application,
) : SubscriptionBillingProvider, PurchasesUpdatedListener {
    private val _uiState = MutableStateFlow(SubscriptionUiState())
    override val uiState: StateFlow<SubscriptionUiState> = _uiState
    private val productDetailsById = mutableMapOf<String, ProductDetails>()
    private var isClosed = false

    private val billingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (isClosed) return
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    fetchProducts()
                    queryActivePurchases()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEntitlementResolved = true,
                            alertMessage = "Billing unavailable: ${result.debugMessage}",
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    override fun selectProduct(productId: String) {
        _uiState.update { it.copy(selectedProductId = productId) }
    }

    override fun purchase(
        activity: Activity,
        launchResolution: (SubscriptionCheckoutResolution) -> Unit,
    ) {
        if (_uiState.value.isPurchasing) return
        val details = productDetailsById[_uiState.value.selectedProductId] ?: return
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        _uiState.update { it.copy(isPurchasing = true, alertMessage = null, purchaseSuccessful = false) }
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.update {
                it.copy(isPurchasing = false, alertMessage = result.debugMessage.ifBlank { "Purchase failed." })
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (isClosed) return
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases, fromCheckout = true)
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _uiState.update { it.copy(isPurchasing = false) }
        } else {
            _uiState.update {
                it.copy(isPurchasing = false, alertMessage = result.debugMessage.ifBlank { "Purchase failed." })
            }
        }
    }

    override fun restorePurchases() = queryActivePurchases(fromRestoreAction = true)

    override fun consumeRestoreToast() {
        _uiState.update { it.copy(restoreToastMessage = null) }
    }

    override fun close() {
        isClosed = true
        billingClient.endConnection()
    }

    private fun fetchProducts() {
        _uiState.update { it.copy(isLoading = true) }
        val products = Monetization.config.productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        ) { result, queryResult ->
            if (isClosed) return@queryProductDetailsAsync
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _uiState.update {
                    it.copy(isLoading = false, alertMessage = "Failed to load subscriptions: ${result.debugMessage}")
                }
                return@queryProductDetailsAsync
            }
            productDetailsById.clear()
            queryResult.productDetailsList.forEach { productDetailsById[it.productId] = it }
            val order = Monetization.config.productIds.withIndex().associate { it.value to it.index }
            val productsUi = queryResult.productDetailsList.mapNotNull(::mapProduct)
                .sortedBy { order[it.id] ?: Int.MAX_VALUE }
            val defaultId = productsUi.firstOrNull { it.id == Monetization.config.defaultProductId }?.id
                ?: productsUi.firstOrNull()?.id
            _uiState.update {
                it.copy(isLoading = false, products = productsUi, selectedProductId = it.selectedProductId ?: defaultId)
            }
        }
    }

    private fun queryActivePurchases(
        fromRestoreAction: Boolean = false,
        fromCheckout: Boolean = false,
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (isClosed) return@queryPurchasesAsync
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases, fromRestoreAction, fromCheckout)
            } else {
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        isEntitlementResolved = true,
                        restoreToastMessage = if (fromRestoreAction) {
                            "Could not restore purchases. Please try again."
                        } else it.restoreToastMessage,
                    )
                }
            }
        }
    }

    private fun handlePurchases(
        purchases: List<Purchase>,
        fromRestoreAction: Boolean = false,
        fromCheckout: Boolean = false,
    ) {
        var hasActive = false
        purchases.forEach { purchase ->
            val belongsToThisAppConfig = purchase.products.any { productId ->
                productId in Monetization.config.productIds
            }
            if (belongsToThisAppConfig && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                hasActive = true
                if (!purchase.isAcknowledged) {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(params) { }
                }
            }
        }
        _uiState.update {
            it.copy(
                isSubscribed = hasActive,
                isEntitlementResolved = true,
                isAdEligible = !hasActive,
                isPurchasing = if (fromCheckout) false else it.isPurchasing,
                purchaseSuccessful = if (fromCheckout) hasActive else it.purchaseSuccessful,
                alertMessage = if (fromCheckout && hasActive) "Purchase successful!" else it.alertMessage,
                restoreToastMessage = if (fromRestoreAction) {
                    if (hasActive) "Purchase restored." else Monetization.config.noSubscriptionFoundMessage
                } else it.restoreToastMessage,
            )
        }
    }

    private fun mapProduct(details: ProductDetails): SubscriptionProduct? {
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return null
        val phase = offer.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros > 0 }
            ?: offer.pricingPhases.pricingPhaseList.firstOrNull()
            ?: return null
        return buildProduct(
            id = details.productId,
            title = details.title,
            priceMicros = phase.priceAmountMicros,
            currencyCode = phase.priceCurrencyCode,
            billingPeriod = phase.billingPeriod,
            offerToken = offer.offerToken,
            hasFreeTrial = offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L },
        )
    }

    private fun buildProduct(
        id: String,
        title: String,
        priceMicros: Long,
        currencyCode: String,
        billingPeriod: String,
        offerToken: String,
        hasFreeTrial: Boolean,
    ): SubscriptionProduct {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val price = BigDecimal(priceMicros).divide(BigDecimal(1_000_000))
        val formatter = NumberFormat.getCurrencyInstance().apply {
            if (currency != null) this.currency = currency
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            roundingMode = RoundingMode.DOWN
        }
        val weekly = when (billingPeriod) {
            "P1M" -> price.divide(BigDecimal(4), 6, RoundingMode.DOWN)
            "P1Y" -> price.divide(BigDecimal(52), 6, RoundingMode.DOWN)
            else -> price
        }
        return SubscriptionProduct(
            id = id,
            title = title,
            priceFormatted = formatter.format(price),
            billingPeriod = billingPeriod,
            offerToken = offerToken,
            hasFreeTrial = hasFreeTrial,
            weeklyEquivalentText = "${formatter.format(weekly)} / ${Monetization.config.weekLabel}",
            label = Monetization.config.productLabels[id] ?: title,
        )
    }
}
