package com.espressotech.monetization.subscriptions

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.espressotech.monetization.Monetization
import com.espressotech.monetization.MarketAccessPolicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SubscriptionViewModel(app: Application) : AndroidViewModel(app) {
    private val billingProvider = StoreBillingProviderFactory.create(app)

    val uiState = billingProvider.uiState
        .map(SubscriptionUiState::withAccessOverride)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = billingProvider.uiState.value.withAccessOverride(),
        )

    fun onHostReady(
        activity: Activity,
        launchResolution: (SubscriptionCheckoutResolution) -> Unit,
    ) = billingProvider.onHostReady(activity, launchResolution)

    fun selectProduct(productId: String) = billingProvider.selectProduct(productId)

    fun purchase(
        activity: Activity,
        launchResolution: (SubscriptionCheckoutResolution) -> Unit,
    ) = billingProvider.purchase(activity, launchResolution)

    fun handlePurchaseResult(resultCode: Int, data: Intent?) =
        billingProvider.handlePurchaseResult(resultCode, data)

    fun restorePurchases() = billingProvider.restorePurchases()

    fun consumeRestoreToast() = billingProvider.consumeRestoreToast()

    override fun onCleared() {
        billingProvider.close()
        super.onCleared()
    }
}

private fun SubscriptionUiState.withAccessOverride(): SubscriptionUiState {
    if (!Monetization.config.bypassSubscriptionAndAds) {
        return copy(isSubscribed = MarketAccessPolicy.hasPremiumAccess(isSubscribed))
    }

    return copy(
        isSubscribed = true,
        isEntitlementResolved = true,
        isAdEligible = false,
    )
}
