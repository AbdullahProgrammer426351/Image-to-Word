package com.espressotech.monetization.subscriptions

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

interface SubscriptionBillingProvider {
    val uiState: StateFlow<SubscriptionUiState>

    fun onHostReady(
        activity: Activity,
        launchResolution: (SubscriptionCheckoutResolution) -> Unit,
    ) = Unit
    fun selectProduct(productId: String)
    fun purchase(activity: Activity, launchResolution: (SubscriptionCheckoutResolution) -> Unit)
    fun handlePurchaseResult(resultCode: Int, data: Intent?) = Unit
    fun restorePurchases()
    fun consumeRestoreToast()
    fun close()
}

sealed interface SubscriptionCheckoutResolution {
    data class Pending(val value: PendingIntent) : SubscriptionCheckoutResolution
    data class Direct(val value: Intent) : SubscriptionCheckoutResolution
}
