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
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.image.word.converter.convert.docx.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubscriptionViewModel(val app: Application) : AndroidViewModel(app), PurchasesUpdatedListener {
    private val logTag = "SUB_DEBUG"
    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState

    private val productIds = listOf(PRODUCT_WEEKLY, PRODUCT_MONTHLY)

    private val billingClient: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    init {
        startConnection()
    }

    fun selectProduct(productId: String) {
        _uiState.update { it.copy(selectedProductId = productId) }
    }

    fun purchase(activity: Activity) {
        val selectedId = _uiState.value.selectedProductId ?: return
        val productDetails = productDetailsById[selectedId] ?: return
        val offerDetails = productDetails.subscriptionOfferDetails?.firstOrNull() ?: return
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerDetails.offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .build()

        _uiState.update { it.copy(isPurchasing = true, alertMessage = null, purchaseSuccessful = false) }
        billingClient.launchBillingFlow(activity, flowParams)
    }

    fun restorePurchases() {
        viewModelScope.launch {
            queryActivePurchases(fromRestoreAction = true)
        }
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

        viewModelScope.launch {
            handlePurchases(purchases)
        }
    }

    private fun startConnection() {
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        viewModelScope.launch {
                            fetchProducts()
                            queryActivePurchases()
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                alertMessage = app.getString(
                                    R.string.billing_unavailable,
                                    result.debugMessage
                                ),
                            )
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {}
            },
        )
    }

    private suspend fun fetchProducts() {
        _uiState.update { it.copy(isLoading = true) }
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        alertMessage = app.getString(
                            R.string.failed_to_load_subscriptions,
                            result.debugMessage
                        ),
                    )
                }
                return@queryProductDetailsAsync
            }

            productDetailsById.clear()
            productDetailsList.forEach { details ->
                productDetailsById[details.productId] = details
            }
            val mapped = productDetailsList.mapNotNull { mapToSubscriptionProduct(it) }
            val productOrder = productIds.withIndex().associate { it.value to it.index }
            val sorted = mapped.sortedBy { productOrder[it.id] ?: Int.MAX_VALUE }

            val defaultId = sorted.firstOrNull { it.id == PRODUCT_MONTHLY }?.id ?: sorted.firstOrNull()?.id

            _uiState.update {
                it.copy(
                    isLoading = false,
                    products = sorted,
                    selectedProductId = it.selectedProductId ?: defaultId,
                )
            }
        }
    }

    private suspend fun queryActivePurchases(fromRestoreAction: Boolean = false) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                viewModelScope.launch { handlePurchases(purchases, fromRestoreAction) }
            } else {
                _uiState.update { it.copy(isPurchasing = false) }
            }
        }
    }

    private suspend fun handlePurchases(
        purchases: List<Purchase>,
        fromRestoreAction: Boolean = false,
    ) {
        var hasActive = false
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                hasActive = true
                if (!purchase.isAcknowledged) {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(params) {}
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
                    app.getString(R.string.no_subscription_found)
                } else {
                    it.restoreToastMessage
                },
            )
        }
    }

    private fun mapToSubscriptionProduct(details: ProductDetails): SubscriptionProduct? {
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return null
        val pricingPhase = offer.pricingPhases.pricingPhaseList
            .firstOrNull { it.priceAmountMicros > 0 } ?: offer.pricingPhases.pricingPhaseList.firstOrNull()
            ?: return null

        val currency = try { Currency.getInstance(pricingPhase.priceCurrencyCode) } catch (_: Exception) { null }
        val price = BigDecimal(pricingPhase.priceAmountMicros).divide(BigDecimal(1_000_000))
        val formatter = NumberFormat.getCurrencyInstance().apply {
            if (currency != null) this.currency = currency
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            roundingMode = RoundingMode.DOWN
        }
        val formatted = formatter.format(price)
        val code = formatter.currency?.currencyCode ?: ""

        val formattedPrice = formatted.replace(Regex("^$code\\s*"), "$code ")
        val billingPeriod = pricingPhase.billingPeriod ?: ""
        val weeklyEquivalentText = weeklyEquivalent(price, formatter, billingPeriod)

        return SubscriptionProduct(
            id = details.productId,
            title = details.title,
            priceFormatted = formattedPrice,
            billingPeriod = billingPeriod,
            label = app.getString(getLabel(details.productId)),
            offerToken = offer.offerToken,
            weeklyEquivalentText = weeklyEquivalentText,
            priceAmountMicros = pricingPhase.priceAmountMicros,
            priceCurrencyCode = pricingPhase.priceCurrencyCode,
        )
    }

    @StringRes
    private fun getLabel(id: String): Int {
        return when (id) {
            PRODUCT_MONTHLY -> R.string.monthly
            else -> R.string.weekly
        }
    }

    private fun weeklyEquivalent(price: BigDecimal, formatter: NumberFormat, billingPeriod: String): String {
        val weekly = when (billingPeriod) {
            "P1W" -> price
            "P1M" -> price.divide(BigDecimal(4), 6, RoundingMode.DOWN)
            "P1Y" -> price.divide(BigDecimal(52), 6, RoundingMode.DOWN)
            else -> price
        }
        return "${formatter.format(weekly)} / ${app.getString(R.string.week)}"
    }

    companion object {
        const val PRODUCT_WEEKLY = "imagetoword_1w"
        const val PRODUCT_MONTHLY = "imagetoword_1m"
    }
}
