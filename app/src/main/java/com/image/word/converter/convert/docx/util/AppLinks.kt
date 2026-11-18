package com.image.word.converter.convert.docx.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.espressotech.monetization.store.StoreLinks

object AppLinks {
    private const val PRIVACY_URL = "https://health-fitness-pro.fit/privacy"
    private const val TERMS_URL = "https://health-fitness-pro.fit/terms"
    private const val DISCLAIMER_URL = "https://health-fitness-pro.fit/disclaimer/"
    private const val FEEDBACK_EMAIL = "espressotechapp@gmail.com"

    fun openPrivacy(context: Context) = openUrl(context, PRIVACY_URL)
    fun openTerms(context: Context) = openUrl(context, TERMS_URL)
    fun openDisclaimer(context: Context) = openUrl(context, DISCLAIMER_URL)

    fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    fun sendFeedback(context: Context) {
        val uri = ("mailto:$FEEDBACK_EMAIL?subject=" +
                Uri.encode("Image To Word Feedback (${StoreLinks.feedbackPlatformName})")).toUri()

        val intent = Intent(Intent.ACTION_SENDTO, uri)

        runCatching {
            context.startActivity(intent)
        }
    }
}
