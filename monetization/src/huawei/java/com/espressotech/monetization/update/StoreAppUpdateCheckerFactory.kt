package com.espressotech.monetization.update

object StoreAppUpdateCheckerFactory {
    fun create(): AppUpdateChecker = HuaweiAppUpdateChecker()
}
