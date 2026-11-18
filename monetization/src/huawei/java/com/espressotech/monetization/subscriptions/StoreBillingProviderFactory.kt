package com.espressotech.monetization.subscriptions

import android.app.Application

object StoreBillingProviderFactory {
    fun create(app: Application): SubscriptionBillingProvider = HuaweiSubscriptionBillingProvider(app)
}
