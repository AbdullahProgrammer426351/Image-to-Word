package com.espressotech.monetization.store

import android.content.Context
import android.content.Intent
import android.net.Uri

object StoreLinks {
    const val feedbackPlatformName = "Android"

    private const val DEVELOPER_URL =
        "https://play.google.com/store/apps/developer?id=ESPRESSO+TECH+%28SMC-PRIVATE%29+LIMITED"

    fun listingUrl(context: Context): String =
        "https://play.google.com/store/apps/details?id=${context.packageName}"

    fun developerUrl(context: Context): String = DEVELOPER_URL

    fun openListing(context: Context) {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${context.packageName}"),
        )
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(listingUrl(context)))
        runCatching { context.startActivity(marketIntent) }
            .onFailure { runCatching { context.startActivity(webIntent) } }
    }

    fun openDeveloperPage(context: Context) {
        openUrl(context, developerUrl(context))
    }

    fun shareApp(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, listingUrl(context))
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
