package com.image.word.converter.convert.docx.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

object AppLinks {
    private const val PLAY_STORE_ID = "com.image.word.converter.convert.docx"
    private const val PRIVACY_URL = "https://health-fitness-pro.fit/privacy-policy/"
    private const val TERMS_URL = "https://health-fitness-pro.fit/terms-of-use/"
    private const val DISCLAIMER_URL = "https://health-fitness-pro.fit/disclaimer/"
    private const val DEVELOPER_URL = "https://play.google.com/store/apps/developer?id=Health+Fitness+Pro"
    private const val FEEDBACK_EMAIL = "support@health-fitness-pro.fit"

    fun openPrivacy(context: Context) = openUrl(context, PRIVACY_URL)
    fun openTerms(context: Context) = openUrl(context, TERMS_URL)
    fun openDisclaimer(context: Context) = openUrl(context, DISCLAIMER_URL)
    fun openDeveloperPage(context: Context) = openUrl(context, DEVELOPER_URL)

    fun shareApp(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "https://play.google.com/store/apps/details?id=$PLAY_STORE_ID",
            )
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun sendFeedback(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:$FEEDBACK_EMAIL".toUri()
            putExtra(Intent.EXTRA_SUBJECT, "Image To Word Feedback")
        }
        runCatching { context.startActivity(intent) }
    }

    fun rateApp(context: Context) {
        val marketUri = "market://details?id=$PLAY_STORE_ID".toUri()
        val webUri = "https://play.google.com/store/apps/details?id=$PLAY_STORE_ID".toUri()
        val marketIntent = Intent(Intent.ACTION_VIEW, marketUri)
        if (marketIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(marketIntent)
        } else {
            openUrl(context, webUri.toString())
        }
    }

    private fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
