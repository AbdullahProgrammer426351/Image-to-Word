package com.espressotech.monetization.update

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.util.Log
import com.huawei.hms.jos.JosApps
import com.huawei.updatesdk.service.appmgr.bean.ApkUpgradeInfo
import com.huawei.updatesdk.service.otaupdate.CheckUpdateCallBack
import com.huawei.updatesdk.service.otaupdate.UpdateKey

class HuaweiAppUpdateChecker : AppUpdateChecker {
    override fun checkUpdate(activity: Activity) {
        runCatching {
            JosApps.getAppUpdateClient(activity).checkAppUpdate(activity, UpdateCallback(activity))
        }.onFailure {
            Log.w(TAG, "Could not start AppGallery update check", it)
        }
    }

    private class UpdateCallback(private val activity: Activity) : CheckUpdateCallBack {
        override fun onUpdateInfo(intent: Intent?) {
            if (intent == null || activity.isFinishing || activity.isDestroyed) return
            val upgradeInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(UpdateKey.INFO, ApkUpgradeInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                (intent.getSerializableExtra(UpdateKey.INFO) as? ApkUpgradeInfo)
            } ?: return
            JosApps.getAppUpdateClient(activity).showUpdateDialog(activity, upgradeInfo, false)
        }

        override fun onMarketInstallInfo(intent: Intent?) = Unit
        override fun onUpdateStoreError(errorCode: Int) {
            Log.w(TAG, "AppGallery update check failed: $errorCode")
        }

        override fun onMarketStoreError(errorCode: Int) {
            Log.w(TAG, "AppGallery store unavailable: $errorCode")
        }
    }

    private companion object {
        const val TAG = "HuaweiAppUpdate"
    }
}
