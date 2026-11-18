package com.espressotech.monetization.update

import android.app.Activity

class NoOpAppUpdateChecker : AppUpdateChecker {
    override fun checkUpdate(activity: Activity) = Unit
}
