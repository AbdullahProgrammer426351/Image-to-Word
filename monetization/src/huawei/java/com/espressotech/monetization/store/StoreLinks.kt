package com.espressotech.monetization.store

import android.content.Context
import android.content.Intent
import android.net.Uri

object StoreLinks {
    const val feedbackPlatformName = "Huawei"

    private const val APP_GALLERY_PACKAGE = "com.huawei.appmarket"
    private const val DEVELOPER_NAME = "ESPRESSO TECH"

    fun listingUrl(context: Context): String =
        "https://appgallery.cloud.huawei.com/appDetail?pkgName=${context.packageName}"

    fun developerUrl(context: Context): String =
        "https://appgallery.huawei.com/search/${Uri.encode(DEVELOPER_NAME)}"

    fun openListing(context: Context) {
        if (openAppGalleryDetails(context)) return
        if (openAppGalleryUri(context, "appmarket://details?id=${context.packageName}")) return
        openUrl(context, listingUrl(context))
    }

    fun openDeveloperPage(context: Context) {
        val searchUri = "appmarket://search?q=${Uri.encode(DEVELOPER_NAME)}"
        if (openAppGalleryUri(context, searchUri)) return
        openUrl(context, developerUrl(context))
    }

    fun shareApp(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, listingUrl(context))
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun openAppGalleryDetails(context: Context): Boolean =
        tryStart(
            context,
            Intent("com.huawei.appmarket.intent.action.AppDetail").apply {
                setPackage(APP_GALLERY_PACKAGE)
                putExtra("APP_PACKAGENAME", context.packageName)
            },
        )

    private fun openAppGalleryUri(context: Context, uri: String): Boolean =
        tryStart(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(APP_GALLERY_PACKAGE)
            },
        )

    private fun tryStart(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess

    private fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
