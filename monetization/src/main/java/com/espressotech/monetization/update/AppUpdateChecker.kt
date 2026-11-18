package com.espressotech.monetization.update

import android.app.Activity

interface AppUpdateChecker {
    fun checkUpdate(activity: Activity)
}
