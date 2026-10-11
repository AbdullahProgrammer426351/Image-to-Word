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
)

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
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}

interface BackendClient {
    suspend fun bootstrapUser(): BackendResult<AccountState>
    suspend fun getAccountState(): BackendResult<AccountState>
}

class HttpBackendClient(
    private val environment: BackendEnvironment,
    private val authSession: AuthSession,
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

    private suspend fun executeAccountRequest(
        function: BackendFunction,
        method: String,
        body: String?,
    ): BackendResult<AccountState> {
        if (!environment.accountIntegrationEnabled) {
            return BackendResult.Failure(BackendFailure.IntegrationDisabled)
        }
        return executeOnce(function, method, body, forceRefresh = false).let { first ->
            if (first.isExpiredAuthenticationFailure()) {
                executeOnce(function, method, body, forceRefresh = true)
            } else {
                first
            }
        }
    }

    private suspend fun executeOnce(
        function: BackendFunction,
        method: String,
        body: String?,
        forceRefresh: Boolean,
    ): BackendResult<AccountState> {
        val token = authSession.idToken(forceRefresh).getOrElse {
            return BackendResult.Failure(BackendFailure.Authentication(it))
        }
        val headers = buildMap {
            put("Authorization", "Bearer $token")
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

        return if (response.statusCode in 200..299) {
            BackendJsonParser.parseAccountEnvelope(response.body)
        } else {
            BackendJsonParser.parseErrorEnvelope(response.body, response.statusCode)
        }
    }

    private fun BackendResult<AccountState>.isExpiredAuthenticationFailure(): Boolean {
        val error = (this as? BackendResult.Failure)?.failure as? BackendFailure.Server ?: return false
        return error.error.httpStatus == 403 && error.error.code == "UNAUTHENTICATED"
    }
}

class DisabledBackendClient : BackendClient {
    override suspend fun bootstrapUser(): BackendResult<AccountState> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun getAccountState(): BackendResult<AccountState> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
}
