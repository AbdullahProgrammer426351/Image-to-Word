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
        val error = JSONObject(raw).getJSONObject("error")
        BackendResult.Failure(
            BackendFailure.Server(
                BackendError(
                    code = error.getString("code"),
                    message = error.getString("message"),
                    httpStatus = httpStatus,
                ),
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
            ),
        )
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.nullableBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else getBoolean(key)
}
