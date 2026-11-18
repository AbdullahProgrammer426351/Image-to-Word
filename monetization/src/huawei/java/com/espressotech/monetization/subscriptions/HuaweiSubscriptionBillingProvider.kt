package com.espressotech.monetization.subscriptions

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.util.Log
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapApiException
import com.huawei.hms.iap.IapClient
import com.huawei.hms.iap.util.IapClientHelper
import com.huawei.hms.iap.entity.OrderStatusCode
import com.huawei.hms.iap.entity.InAppPurchaseData
import com.huawei.hms.iap.entity.OwnedPurchasesReq
import com.huawei.hms.iap.entity.ProductInfoReq
import com.huawei.hms.iap.entity.PurchaseIntentReq
import com.espressotech.monetization.Monetization
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class HuaweiSubscriptionBillingProvider(
    private val app: Application,
) : SubscriptionBillingProvider {
    private val _uiState = MutableStateFlow(SubscriptionUiState())
    override val uiState: StateFlow<SubscriptionUiState> = _uiState
    private val iapClient: IapClient = Iap.getIapClient(app)
    private var isClosed = false
    private var isEnvironmentReady = false
    private var isEnvironmentCheckInFlight = false
    private var pendingOperation: PendingOperation? = null

    init {
        debugLog(
            "Provider created: package=${app.packageName}, " +
                "catalog=${Monetization.config.productIds}, " +
                "default=${Monetization.config.defaultProductId}",
        )
        runCatching { iapClient.enablePendingPurchase() }
            .onFailure { Log.w(TAG, "Unable to enable pending purchases", it) }
    }

    override fun onHostReady(
        activity: Activity,
        launchResolution: (SubscriptionCheckoutResolution) -> Unit,
    ) {
        debugLog(
            "onHostReady: closed=$isClosed, environmentReady=$isEnvironmentReady, " +
                "environmentCheckInFlight=$isEnvironmentCheckInFlight, pendingOperation=$pendingOperation",
        )
        if (isClosed || isEnvironmentReady || isEnvironmentCheckInFlight ||
            pendingOperation == PendingOperation.ENVIRONMENT
        ) return
        checkEnvironment(activity, launchResolution)
    }

    override fun selectProduct(productId: String) {
        debugLog("Product selected: id=$productId")
        _uiState.update { it.copy(selectedProductId = productId) }
    }

    override fun purchase(
        activity: Activity,
        launchResolution: (SubscriptionCheckoutResolution) -> Unit,
    ) {
        if (_uiState.value.isPurchasing) return
        val selectedId = _uiState.value.selectedProductId ?: return
        _uiState.update { it.copy(isPurchasing = true, alertMessage = null, purchaseSuccessful = false) }
        val request = PurchaseIntentReq().apply {
            productId = selectedId
            priceType = IapClient.PriceType.IN_APP_SUBSCRIPTION
        }
        iapClient.createPurchaseIntent(request)
            .addOnSuccessListener { result ->
                if (isClosed) return@addOnSuccessListener
                val status = result?.status
                val resolution = when {
                    status?.resolution != null -> SubscriptionCheckoutResolution.Pending(status.resolution)
                    status?.resolutionIntent != null -> SubscriptionCheckoutResolution.Direct(status.resolutionIntent)
                    else -> null
                }
                if (resolution == null) {
                    _uiState.update {
                        it.copy(isPurchasing = false, alertMessage = status?.statusMessage ?: "Purchase failed.")
                    }
                } else {
                    pendingOperation = PendingOperation.PURCHASE
                    runCatching { launchResolution(resolution) }
                        .onFailure {
                            pendingOperation = null
                            _uiState.update { state ->
                                state.copy(isPurchasing = false, alertMessage = "Purchase failed.")
                            }
                        }
                }
            }
            .addOnFailureListener { error ->
                if (isClosed) return@addOnFailureListener
                logFailure("createPurchaseIntent", error)
                if ((error as? IapApiException)?.statusCode == OrderStatusCode.ORDER_PRODUCT_OWNED) {
                    queryActivePurchases(fromCheckout = true)
                } else {
                    _uiState.update {
                        it.copy(isPurchasing = false, alertMessage = error.message ?: "Purchase failed.")
                    }
                }
            }
    }

    override fun handlePurchaseResult(resultCode: Int, data: Intent?) {
        if (isClosed) return
        val operation = pendingOperation
        pendingOperation = null

        if (operation == PendingOperation.ENVIRONMENT) {
            handleEnvironmentResult(data)
            return
        }

        if (data == null) {
            _uiState.update {
                it.copy(
                    isPurchasing = false,
                    alertMessage = if (resultCode == Activity.RESULT_CANCELED) null else "Purchase failed.",
                )
            }
            return
        }
        val result = runCatching { iapClient.parsePurchaseResultInfoFromIntent(data) }.getOrNull()
        when (result?.returnCode) {
            OrderStatusCode.ORDER_STATE_SUCCESS,
            OrderStatusCode.ORDER_PRODUCT_OWNED -> queryActivePurchases(fromCheckout = true)
            OrderStatusCode.ORDER_STATE_CANCEL -> _uiState.update { it.copy(isPurchasing = false) }
            OrderStatusCode.ORDER_STATE_PENDING -> _uiState.update {
                it.copy(isPurchasing = false, alertMessage = "Purchase is pending.")
            }
            else -> _uiState.update { it.copy(isPurchasing = false, alertMessage = "Purchase failed.") }
        }
    }

    override fun restorePurchases() = queryActivePurchases(fromRestoreAction = true)

    override fun consumeRestoreToast() {
        _uiState.update { it.copy(restoreToastMessage = null) }
    }

    override fun close() {
        isClosed = true
        pendingOperation = null
    }

    private fun checkEnvironment(
        activity: Activity,
        launchResolution: (SubscriptionCheckoutResolution) -> Unit,
    ) {
        debugLog("Calling isEnvReady")
        isEnvironmentCheckInFlight = true
        iapClient.isEnvReady
            .addOnSuccessListener {
                if (isClosed) return@addOnSuccessListener
                debugLog("isEnvReady succeeded; fetching products and owned subscriptions")
                isEnvironmentCheckInFlight = false
                isEnvironmentReady = true
                fetchProducts()
                queryActivePurchases()
            }
            .addOnFailureListener { error ->
                if (isClosed) return@addOnFailureListener
                isEnvironmentCheckInFlight = false
                logFailure("isEnvReady", error)
                val apiError = error as? IapApiException
                val status = apiError?.status
                debugLog(
                    "isEnvReady failure details: statusCode=${apiError?.statusCode}, " +
                        "hasResolution=${status?.hasResolution()}, statusMessage=${status?.statusMessage}",
                )
                if (apiError?.statusCode == OrderStatusCode.ORDER_HWID_NOT_LOGIN &&
                    status != null && status.hasResolution()
                ) {
                    val resolution = when {
                        status.resolution != null -> SubscriptionCheckoutResolution.Pending(status.resolution)
                        status.resolutionIntent != null -> SubscriptionCheckoutResolution.Direct(status.resolutionIntent)
                        else -> null
                    }
                    if (resolution != null) {
                        pendingOperation = PendingOperation.ENVIRONMENT
                        runCatching { launchResolution(resolution) }
                            .onFailure {
                                pendingOperation = null
                                markEnvironmentUnavailable(it.message)
                            }
                        return@addOnFailureListener
                    }
                }
                markEnvironmentUnavailable(error.message)
            }
    }

    private fun handleEnvironmentResult(data: Intent?) {
        val returnCode = data?.let(IapClientHelper::parseRespCodeFromIntent)
        debugLog(
            "Environment resolution returned: resultCode=$returnCode, " +
                "message=${data?.let(IapClientHelper::parseRespMessageFromIntent)}",
        )
        if (returnCode == OrderStatusCode.ORDER_STATE_SUCCESS) {
            isEnvironmentReady = true
            fetchProducts()
            queryActivePurchases()
        } else {
            markEnvironmentUnavailable(
                data?.let(IapClientHelper::parseRespMessageFromIntent),
            )
        }
    }

    private fun markEnvironmentUnavailable(message: String?) {
        if (isClosed) return
        val allowDebugAds = Monetization.config.isDebug
        debugLog(
            "Environment unavailable: message=$message, " +
                "allowDebugAds=$allowDebugAds",
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                isEntitlementResolved = true,
                // Non-Huawei test devices may not have HMS Core, so Huawei IAP cannot
                // resolve entitlement there. Keep release fail-closed, but allow the
                // Huawei debug variant to exercise Petal test ads.
                isAdEligible = allowDebugAds,
                alertMessage = "Billing unavailable: ${message.orEmpty()}",
            )
        }
    }

    private fun fetchProducts() {
        debugLog(
            "Calling obtainProductInfo: priceType=${IapClient.PriceType.IN_APP_SUBSCRIPTION}, " +
                "requestedIds=${Monetization.config.productIds}",
        )
        _uiState.update { it.copy(isLoading = true) }
        val request = ProductInfoReq().apply {
            productIds = ArrayList(Monetization.config.productIds)
            priceType = IapClient.PriceType.IN_APP_SUBSCRIPTION
        }
        iapClient.obtainProductInfo(request)
            .addOnSuccessListener { result ->
                if (isClosed) return@addOnSuccessListener
                val returnedProducts = result?.productInfoList.orEmpty()
                debugLog(
                    "obtainProductInfo succeeded: resultNull=${result == null}, " +
                        "returnedCount=${returnedProducts.size}, " +
                        "returnedIds=${returnedProducts.map { it.productId }}",
                )
                returnedProducts.forEach { info ->
                    debugLog(
                        "Returned product: id=${info.productId}, name=${info.productName}, " +
                            "currency=${info.currency}, microsPrice=${info.microsPrice}, " +
                            "subPeriod=${info.subPeriod}",
                    )
                }
                val order = Monetization.config.productIds.withIndex().associate { it.value to it.index }
                val products = returnedProducts.map { info ->
                    mapProduct(
                        id = info.productId,
                        title = info.productName ?: info.productId,
                        priceMicros = info.microsPrice,
                        currencyCode = info.currency ?: "USD",
                        billingPeriod = info.subPeriod ?: "",
                    )
                }.sortedBy { order[it.id] ?: Int.MAX_VALUE }
                val defaultId = products.firstOrNull { it.id == Monetization.config.defaultProductId }?.id
                    ?: products.firstOrNull()?.id
                debugLog(
                    "Product UI state: mappedCount=${products.size}, defaultId=$defaultId, " +
                        "subscribeEnabled=${defaultId != null}",
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        products = products,
                        selectedProductId = it.selectedProductId ?: defaultId,
                        alertMessage = if (products.isEmpty()) "No subscription products were found." else null,
                    )
                }
            }
            .addOnFailureListener { error ->
                if (isClosed) return@addOnFailureListener
                logFailure("obtainProductInfo", error)
                debugLog(
                    "Product UI state after failure: isLoading=false, selectedProductId=${_uiState.value.selectedProductId}",
                )
                _uiState.update {
                    it.copy(isLoading = false, alertMessage = "Failed to load subscriptions: ${error.message.orEmpty()}")
                }
            }
    }

    private fun queryActivePurchases(
        fromRestoreAction: Boolean = false,
        fromCheckout: Boolean = false,
        continuationToken: String? = null,
        accumulatedPurchaseData: MutableList<String> = mutableListOf(),
        seenTokens: MutableSet<String> = mutableSetOf(),
    ) {
        val request = OwnedPurchasesReq().apply {
            priceType = IapClient.PriceType.IN_APP_SUBSCRIPTION
            if (!continuationToken.isNullOrBlank()) {
                this.continuationToken = continuationToken
            }
        }
        iapClient.obtainOwnedPurchases(request)
            .addOnSuccessListener { result ->
                if (isClosed) return@addOnSuccessListener
                accumulatedPurchaseData += result?.inAppPurchaseDataList.orEmpty()

                val nextToken = result?.continuationToken?.takeIf { it.isNotBlank() }
                if (nextToken != null && seenTokens.add(nextToken)) {
                    queryActivePurchases(
                        fromRestoreAction = fromRestoreAction,
                        fromCheckout = fromCheckout,
                        continuationToken = nextToken,
                        accumulatedPurchaseData = accumulatedPurchaseData,
                        seenTokens = seenTokens,
                    )
                    return@addOnSuccessListener
                }

                val hasActive = accumulatedPurchaseData.any { data ->
                    runCatching {
                        InAppPurchaseData(data).let { purchase ->
                            purchase.purchaseState == 0 &&
                                purchase.isSubValid &&
                                purchase.productId in Monetization.config.productIds
                        }
                    }.onFailure {
                        Log.w(TAG, "Could not parse owned subscription data", it)
                    }.getOrDefault(false)
                }
                _uiState.update {
                    it.copy(
                        isSubscribed = hasActive,
                        isEntitlementResolved = true,
                        isAdEligible = !hasActive,
                        isPurchasing = if (fromCheckout) false else it.isPurchasing,
                        purchaseSuccessful = if (fromCheckout) hasActive else it.purchaseSuccessful,
                        alertMessage = if (fromCheckout) {
                            if (hasActive) "Purchase successful!" else "The purchase could not be confirmed."
                        } else it.alertMessage,
                        restoreToastMessage = if (fromRestoreAction) {
                            if (hasActive) "Purchase restored." else Monetization.config.noSubscriptionFoundMessage
                        } else it.restoreToastMessage,
                    )
                }
            }
            .addOnFailureListener { error ->
                if (isClosed) return@addOnFailureListener
                logFailure("obtainOwnedPurchases", error)
                _uiState.update {
                    it.copy(
                        isPurchasing = if (fromCheckout) false else it.isPurchasing,
                        isEntitlementResolved = true,
                        alertMessage = if (fromCheckout) "The purchase could not be confirmed." else it.alertMessage,
                        restoreToastMessage = if (fromRestoreAction) {
                            "Could not restore purchases. Please try again."
                        } else it.restoreToastMessage,
                    )
                }
            }
    }

    private fun mapProduct(
        id: String,
        title: String,
        priceMicros: Long,
        currencyCode: String,
        billingPeriod: String,
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
            offerToken = "",
            hasFreeTrial = false,
            weeklyEquivalentText = "${formatter.format(weekly)} / ${Monetization.config.weekLabel}",
            label = Monetization.config.productLabels[id] ?: title,
        )
    }

    private fun logFailure(operation: String, error: Exception) {
        Log.e(TAG, "$operation failed with ${(error as? IapApiException)?.statusCode}: ${error.message}", error)
    }

    private fun debugLog(message: String) {
        if (Monetization.config.isDebug) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "HuaweiIapSubscriptions"
    }

    private enum class PendingOperation {
        ENVIRONMENT,
        PURCHASE,
    }
}
