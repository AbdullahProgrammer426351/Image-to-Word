package com.image.word.converter.convert.docx.backend

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackendHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
) {
    override fun toString(): String =
        "BackendHttpRequest(method=$method, url=$url, headerNames=${headers.keys.sorted()}, bodyPresent=${body != null})"
}

data class BackendHttpResponse(
    val statusCode: Int,
    val body: String,
)

interface BackendHttpTransport {
    suspend fun execute(request: BackendHttpRequest): BackendHttpResponse
}

class UrlConnectionBackendTransport : BackendHttpTransport {
    override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(request.url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = request.method
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                request.headers.forEach(connection::setRequestProperty)
                if (request.body != null) {
                    connection.doOutput = true
                    connection.outputStream.use { output ->
                        output.write(request.body.toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                BackendHttpResponse(
                    statusCode = status,
                    body = stream?.bufferedReader()?.use { it.readText() }.orEmpty(),
                )
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 120_000
    }
}

interface BackendClient {
    suspend fun bootstrapUser(): BackendResult<AccountState>
    suspend fun getAccountState(): BackendResult<AccountState>
    suspend fun imageToWord(request: ImageConversionRequest): BackendResult<ImageConversionResponse>
    suspend fun verifySubscriptionPurchase(
        request: SubscriptionVerificationRequest,
    ): BackendResult<AccountState>
    suspend fun getCreditProductCatalog(): BackendResult<List<CreditProduct>> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
    suspend fun verifyCreditPurchase(
        request: CreditPurchaseVerificationRequest,
    ): BackendResult<CreditPurchaseVerificationResponse> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
    suspend fun recoverCreditPurchase(
        purchaseToken: String,
    ): BackendResult<CreditPurchaseVerificationResponse> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
}

class HttpBackendClient(
    private val environment: BackendEnvironment,
    private val authSession: AuthSession,
    private val appCheckSession: AppCheckSession,
    private val transport: BackendHttpTransport,
) : BackendClient {
    override suspend fun bootstrapUser(): BackendResult<AccountState> = executeAccountRequest(
        function = BackendFunction.BOOTSTRAP_USER,
        method = "POST",
        body = "{}",
    )

    override suspend fun getAccountState(): BackendResult<AccountState> = executeAccountRequest(
        function = BackendFunction.GET_ACCOUNT_STATE,
        method = "GET",
        body = null,
    )

    override suspend fun imageToWord(
        request: ImageConversionRequest,
    ): BackendResult<ImageConversionResponse> {
        val body = org.json.JSONObject()
            .put("image", request.image)
            .put("idempotencyKey", request.idempotencyKey)
            .toString()
        return executeAuthenticated(
            function = BackendFunction.IMAGE_TO_WORD,
            method = "POST",
            body = body,
        ) { response ->
            BackendJsonParser.parseImageConversionEnvelope(response.body, response.statusCode)
        }
    }

    override suspend fun verifySubscriptionPurchase(
        request: SubscriptionVerificationRequest,
    ): BackendResult<AccountState> {
        val body = org.json.JSONObject()
            .put("productId", request.productId)
            .put("purchaseToken", request.purchaseToken)
            .toString()
        return executeAuthenticated(
            function = BackendFunction.VERIFY_SUBSCRIPTION_PURCHASE,
            method = "POST",
            body = body,
        ) { response ->
            BackendJsonParser.parseSubscriptionVerificationEnvelope(
                response.body,
                response.statusCode,
            )
        }
    }

    override suspend fun getCreditProductCatalog(): BackendResult<List<CreditProduct>> =
        executeAuthenticated(
            function = BackendFunction.GET_CREDIT_PRODUCT_CATALOG,
            method = "GET",
            body = null,
        ) { response ->
            BackendJsonParser.parseCreditProductCatalogEnvelope(response.body, response.statusCode)
        }

    override suspend fun verifyCreditPurchase(
        request: CreditPurchaseVerificationRequest,
    ): BackendResult<CreditPurchaseVerificationResponse> {
        val body = org.json.JSONObject()
            .put("productId", request.productId)
            .put("purchaseToken", request.purchaseToken)
            .toString()
        return executeAuthenticated(
            function = BackendFunction.VERIFY_CREDIT_PURCHASE,
            method = "POST",
            body = body,
        ) { response ->
            BackendJsonParser.parseCreditPurchaseVerificationEnvelope(
                response.body,
                response.statusCode,
            )
        }
    }

    override suspend fun recoverCreditPurchase(
        purchaseToken: String,
    ): BackendResult<CreditPurchaseVerificationResponse> {
        val body = org.json.JSONObject().put("purchaseToken", purchaseToken).toString()
        return executeAuthenticated(
            function = BackendFunction.RECOVER_CREDIT_PURCHASE,
            method = "POST",
            body = body,
        ) { response ->
            BackendJsonParser.parseCreditPurchaseVerificationEnvelope(
                response.body,
                response.statusCode,
            )
        }
    }

    private suspend fun executeAccountRequest(
        function: BackendFunction,
        method: String,
        body: String?,
    ): BackendResult<AccountState> {
        if (!environment.accountIntegrationEnabled) {
            return BackendResult.Failure(BackendFailure.IntegrationDisabled)
        }
        return executeAuthenticated(function, method, body) { response ->
            if (response.statusCode in 200..299) {
                BackendJsonParser.parseAccountEnvelope(response.body)
            } else {
                BackendJsonParser.parseErrorEnvelope(response.body, response.statusCode)
            }
        }
    }

    private suspend fun <T> executeAuthenticated(
        function: BackendFunction,
        method: String,
        body: String?,
        parse: (BackendHttpResponse) -> BackendResult<T>,
    ): BackendResult<T> {
        if (!environment.accountIntegrationEnabled) {
            return BackendResult.Failure(BackendFailure.IntegrationDisabled)
        }
        var authRefreshAttempted = false
        var appCheckRefreshAttempted = false
        var forceAuthRefresh = false
        var forceAppCheckRefresh = false
        while (true) {
            val result = executeOnce(
                function = function,
                method = method,
                body = body,
                forceAuthRefresh = forceAuthRefresh,
                forceAppCheckRefresh = forceAppCheckRefresh,
                parse = parse,
            )
            forceAuthRefresh = false
            forceAppCheckRefresh = false
            when {
                result.isExpiredAuthenticationFailure() && !authRefreshAttempted -> {
                    authRefreshAttempted = true
                    forceAuthRefresh = true
                }
                result.requiresAppCheckRefresh() && !appCheckRefreshAttempted -> {
                    appCheckRefreshAttempted = true
                    forceAppCheckRefresh = true
                }
                else -> return result
            }
        }
    }

    private suspend fun <T> executeOnce(
        function: BackendFunction,
        method: String,
        body: String?,
        forceAuthRefresh: Boolean,
        forceAppCheckRefresh: Boolean,
        parse: (BackendHttpResponse) -> BackendResult<T>,
    ): BackendResult<T> {
        val token = authSession.idToken(forceAuthRefresh).getOrElse {
            return BackendResult.Failure(BackendFailure.Authentication(it))
        }
        val appCheckToken = appCheckSession.token(forceAppCheckRefresh).getOrElse {
            return BackendResult.Failure(BackendFailure.AppCheck(it))
        }
        val headers = buildMap {
            put("Authorization", "Bearer $token")
            if (appCheckToken != null) put(APP_CHECK_HEADER, appCheckToken)
            put("Accept", "application/json")
            if (body != null) put("Content-Type", "application/json")
        }
        val response = runCatching {
            transport.execute(
                BackendHttpRequest(
                    method = method,
                    url = environment.endpoint(function),
                    headers = headers,
                    body = body,
                ),
            )
        }.getOrElse { return BackendResult.Failure(BackendFailure.Network(it)) }

        return parse(response)
    }

    private fun BackendResult<*>.isExpiredAuthenticationFailure(): Boolean {
        val error = (this as? BackendResult.Failure)?.failure as? BackendFailure.Server ?: return false
        return error.error.httpStatus == 403 && error.error.code == "UNAUTHENTICATED"
    }

    private fun BackendResult<*>.requiresAppCheckRefresh(): Boolean {
        val failure = (this as? BackendResult.Failure)?.failure ?: return false
        if (failure is BackendFailure.AppCheck) return true
        val error = (failure as? BackendFailure.Server)?.error ?: return false
        return error.httpStatus == 403 && error.code == "APP_CHECK_FAILED"
    }

    private companion object {
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
    }
}

class DisabledBackendClient : BackendClient {
    override suspend fun bootstrapUser(): BackendResult<AccountState> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun getAccountState(): BackendResult<AccountState> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun imageToWord(
        request: ImageConversionRequest,
    ): BackendResult<ImageConversionResponse> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun verifySubscriptionPurchase(
        request: SubscriptionVerificationRequest,
    ): BackendResult<AccountState> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun getCreditProductCatalog(): BackendResult<List<CreditProduct>> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun verifyCreditPurchase(
        request: CreditPurchaseVerificationRequest,
    ): BackendResult<CreditPurchaseVerificationResponse> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun recoverCreditPurchase(
        purchaseToken: String,
    ): BackendResult<CreditPurchaseVerificationResponse> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
}
