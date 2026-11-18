package com.espressotech.monetization

import android.app.Activity
import android.content.Context
import com.espressotech.monetization.config.MonetizationConfig
import com.espressotech.monetization.update.StoreAppUpdateCheckerFactory

object Monetization {
    private var appContext: Context? = null
    private var storedConfig: MonetizationConfig? = null

    val isInitialized: Boolean get() = storedConfig != null

    val config: MonetizationConfig
        get() = storedConfig
            ?: error("Monetization.initialize(context, config) must be called before use")

    val applicationContext: Context
        get() = appContext
            ?: error("Monetization.initialize(context, config) must be called before use")

    fun initialize(context: Context, config: MonetizationConfig) {
        appContext = context.applicationContext
        storedConfig = config
    }

    fun checkForUpdates(activity: Activity) {
        StoreAppUpdateCheckerFactory.create().checkUpdate(activity)
    }
}
