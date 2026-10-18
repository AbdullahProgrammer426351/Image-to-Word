package com.image.word.converter.convert.docx.backend

import org.json.JSONObject

object BackendJsonParser {
    fun parseAccountEnvelope(raw: String): BackendResult<AccountState> = runCatching {
        val root = JSONObject(raw)
        if (!root.optBoolean("success", false)) {
            return parseError(root, 200)
        }
        BackendResult.Success(parseAccount(root.getJSONObject("account")))
    }.getOrElse { BackendResult.Failure(BackendFailure.InvalidResponse(it)) }

    fun parseErrorEnvelope(raw: String, httpStatus: Int): BackendResult.Failure = runCatching {
        val root = JSONObject(raw)
        val error = root.getJSONObject("error")
        val credits = root.optJSONObject("credits")
        BackendResult.Failure(
            BackendFailure.Server(
                BackendError(
                    code = error.getString("code"),
                    message = error.getString("message"),
                    httpStatus = httpStatus,
                    conversionId = root.nullableString("conversionId"),
                    conversionStatus = root.nullableString("status"),
                    requiredCredits = credits?.nullableLong("required"),
                    availableCredits = credits?.nullableLong("available"),
                ),
            ),
        )
    }.getOrElse { BackendResult.Failure(BackendFailure.InvalidResponse(it)) }

    fun parseImageConversionEnvelope(
        raw: String,
        httpStatus: Int,
    ): BackendResult<ImageConversionResponse> = runCatching {
        val root = JSONObject(raw)
        if (!root.optBoolean("success", false)) {
            return parseErrorEnvelope(raw, httpStatus)
        }
        val credits = root.getJSONObject("credits")
        val resultUrl = extractResultUrl(root.get("result"))
            ?: throw IllegalArgumentException("Conversion result did not contain a document URL")
        BackendResult.Success(
            ImageConversionResponse(
                resultUrl = resultUrl,
                conversionId = root.getString("conversionId"),
                credits = ConversionCredits(
                    charged = credits.getLong("charged"),
                    available = credits.getLong("available"),
                ),
                accessMode = root.nullableString("accessMode")?.let { value ->
                    runCatching { ConversionAccessMode.valueOf(value) }
                        .getOrElse { throw IllegalArgumentException("Unknown conversion access mode") }
                },
            ),
        )
    }.getOrElse { BackendResult.Failure(BackendFailure.InvalidResponse(it)) }

    fun parseSubscriptionVerificationEnvelope(
        raw: String,
        httpStatus: Int,
    ): BackendResult<AccountState> = runCatching {
        val root = JSONObject(raw)
        if (!root.optBoolean("success", false)) {
            return parseErrorEnvelope(raw, httpStatus)
        }
        BackendResult.Success(parseAccount(root.getJSONObject("account")))
    }.getOrElse { BackendResult.Failure(BackendFailure.InvalidResponse(it)) }

    fun parseCreditProductCatalogEnvelope(
        raw: String,
        httpStatus: Int,
    ): BackendResult<List<CreditProduct>> = runCatching {
        val root = JSONObject(raw)
        if (!root.optBoolean("success", false)) return parseErrorEnvelope(raw, httpStatus)
        val products = root.getJSONArray("products")
        val parsed = buildList {
            for (index in 0 until products.length()) {
                val item = products.getJSONObject(index)
                val productType = item.getString("productType")
                val productId = item.getString("productId")
                val credits = item.getLong("credits")
                val expiryDays = item.getInt("expiryDays")
                require(productType == "INAPP" && productId.isNotBlank() && credits > 0 && expiryDays > 0) {
                    "Invalid credit product"
                }
                add(CreditProduct(productId, productType, credits, expiryDays))
            }
        }
        require(parsed.map { it.productId }.distinct().size == parsed.size) {
            "Duplicate credit product"
        }
        BackendResult.Success(parsed)
    }.getOrElse { BackendResult.Failure(BackendFailure.InvalidResponse(it)) }

    fun parseCreditPurchaseVerificationEnvelope(
        raw: String,
        httpStatus: Int,
    ): BackendResult<CreditPurchaseVerificationResponse> = runCatching {
        val root = JSONObject(raw)
        if (!root.optBoolean("success", false)) return parseErrorEnvelope(raw, httpStatus)
        val purchase = root.getJSONObject("purchase")
        BackendResult.Success(
            CreditPurchaseVerificationResponse(
                purchase = CreditPurchaseReceipt(
                    productId = purchase.getString("productId"),
                    status = purchase.getString("status"),
                    credits = purchase.getLong("credits"),
                ),
                account = parseAccount(root.getJSONObject("account")),
            ),
        )
    }.getOrElse { BackendResult.Failure(BackendFailure.InvalidResponse(it)) }

    private fun parseError(root: JSONObject, httpStatus: Int): BackendResult.Failure {
        val error = root.getJSONObject("error")
        return BackendResult.Failure(
            BackendFailure.Server(
                BackendError(
                    code = error.getString("code"),
                    message = error.getString("message"),
                    httpStatus = httpStatus,
                ),
            ),
        )
    }

    private fun parseAccount(json: JSONObject): AccountState {
        val user = json.getJSONObject("user")
        val credits = json.getJSONObject("credits")
        val subscription = json.getJSONObject("subscription")
        val conversionAccess = json.getJSONObject("conversionAccess")
        val freeConversion = conversionAccess.getJSONObject("freeConversion")
        // Prefer the frozen contract location. The root fallback is temporary compatibility
        // for the first local backend implementation and must be removed once it is aligned.
        val ads = conversionAccess.optJSONObject("ads") ?: json.getJSONObject("ads")
        val mode = runCatching {
            ConversionAccessMode.valueOf(conversionAccess.getString("mode"))
        }.getOrElse { throw IllegalArgumentException("Unknown conversion access mode") }
        return AccountState(
            user = BackendUser(
                id = user.getString("id"),
                userName = user.getString("userName"),
                email = user.nullableString("email"),
                accountType = user.getString("accountType"),
            ),
            credits = CreditsState(
                available = credits.getLong("available"),
                reserved = credits.getLong("reserved"),
                nextExpiryAt = credits.nullableString("nextExpiryAt"),
            ),
            subscription = SubscriptionState(
                plan = subscription.nullableString("plan"),
                status = subscription.nullableString("status"),
                periodEnd = subscription.nullableString("periodEnd"),
                autoRenewing = subscription.nullableBoolean("autoRenewing"),
                acknowledgementState = subscription.nullableString("acknowledgementState"),
                benefitMode = subscription.nullableString("benefitMode"),
            ),
            conversionAccess = ConversionAccessState(
                mode = mode,
                canConvert = conversionAccess.getBoolean("canConvert"),
                freeConversion = FreeConversionState(
                    status = freeConversion.getString("status"),
                    available = freeConversion.getBoolean("available"),
                    successfulConversions = freeConversion.getInt("successfulConversions"),
                    remaining = freeConversion.getInt("remaining"),
                ),
                ads = AdEntitlementState(
                    adFree = ads.getBoolean("adFree"),
                    eligible = ads.getBoolean("eligible"),
                ),
            ),
        )
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.nullableBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else getBoolean(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun extractResultUrl(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }
        is JSONObject -> {
            sequenceOf("file", "url", "resultUrl")
                .mapNotNull { value.nullableString(it) }
                .firstOrNull { it.isNotBlank() }
                ?: sequenceOf("result", "data")
                    .mapNotNull { key -> value.opt(key).takeUnless { it == null || it == JSONObject.NULL } }
                    .mapNotNull(::extractResultUrl)
                    .firstOrNull()
        }
        else -> null
    }
}
