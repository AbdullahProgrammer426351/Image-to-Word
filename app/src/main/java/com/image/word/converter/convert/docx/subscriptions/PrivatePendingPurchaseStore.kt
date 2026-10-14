package com.image.word.converter.convert.docx.subscriptions

import android.content.Context
import com.image.word.converter.convert.docx.backend.SubscriptionVerificationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PrivatePendingPurchaseStore(
    context: Context,
) : PendingPurchaseStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun upsert(request: SubscriptionVerificationRequest) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val entries = readEntries().associateByTo(linkedMapOf()) { it.purchaseToken }
            entries[request.purchaseToken] = request
            writeEntries(entries.values)
        }
    }

    override suspend fun remove(purchaseToken: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            writeEntries(readEntries().filterNot { it.purchaseToken == purchaseToken })
        }
    }

    override suspend fun all(): List<SubscriptionVerificationRequest> = mutex.withLock {
        withContext(Dispatchers.IO) { readEntries() }
    }

    private fun readEntries(): List<SubscriptionVerificationRequest> = runCatching {
        PendingPurchaseCodec.decode(preferences.getString(KEY_PENDING, "[]").orEmpty())
    }.getOrDefault(emptyList())

    private fun writeEntries(entries: Collection<SubscriptionVerificationRequest>) {
        check(preferences.edit().putString(KEY_PENDING, PendingPurchaseCodec.encode(entries)).commit()) {
            "Unable to persist pending purchase verification"
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "pending_subscription_verification"
        const val KEY_PENDING = "requests"
    }
}

internal object PendingPurchaseCodec {
    fun decode(raw: String): List<SubscriptionVerificationRequest> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                runCatching {
                    val item = array.getJSONObject(index)
                    val productId = item.getString("productId")
                    val token = item.getString("purchaseToken")
                    if (productId in BackendSubscriptionVerifier.SUPPORTED_PRODUCT_IDS && token.isNotBlank()) {
                        add(SubscriptionVerificationRequest(productId, token))
                    }
                }
            }
        }.distinctBy { it.purchaseToken }
    }

    fun encode(entries: Collection<SubscriptionVerificationRequest>): String = JSONArray().apply {
        entries.distinctBy { it.purchaseToken }.forEach { request ->
            put(
                JSONObject()
                    .put("productId", request.productId)
                    .put("purchaseToken", request.purchaseToken),
            )
        }
    }.toString()
}
