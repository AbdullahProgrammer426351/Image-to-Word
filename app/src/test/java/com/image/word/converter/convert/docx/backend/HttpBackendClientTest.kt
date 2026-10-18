package com.image.word.converter.convert.docx.backend

import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpBackendClientTest {
    @Test
    fun bootstrap_attachesAuthorizationAndSendsNoUserId() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(200, BackendJsonParserTest.accountEnvelope()),
        )
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            SuccessfulAppCheckSession(),
            transport,
        )

        val result = client.bootstrapUser()

        assertTrue(result is BackendResult.Success)
        val request = transport.requests.single()
        assertEquals("Bearer test-id-token", request.headers["Authorization"])
        assertEquals("test-app-check-token", request.headers["X-Firebase-AppCheck"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("{}", request.body)
        assertFalse(request.body.orEmpty().contains("userId"))
    }

    @Test
    fun debugEnvironment_selectsOnlyEmulatorUrl() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(200, BackendJsonParserTest.accountEnvelope()),
        )
        val environment = emulatorEnvironment()
        val client = HttpBackendClient(
            environment,
            SuccessfulAuthSession(),
            DisabledAppCheckSession,
            transport,
        )

        client.getAccountState()

        val url = transport.requests.single().url
        assertEquals(
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/getAccountStateV2",
            url,
        )
        assertFalse(url.contains("converter-api-project"))
        assertFalse(url.startsWith("https://"))
        assertFalse(transport.requests.single().headers.containsKey("X-Firebase-AppCheck"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emulatorEnvironment_rejectsProductionProject() {
        BackendEnvironment(
            name = BackendEnvironment.EMULATOR,
            accountIntegrationEnabled = true,
            firebaseProjectId = "converter-api-project",
            functionsBaseUrl = BackendEnvironment.DEMO_FUNCTIONS_BASE_URL,
            authEmulatorHost = BackendEnvironment.DEMO_AUTH_HOST,
            authEmulatorPort = BackendEnvironment.DEMO_AUTH_PORT,
            isolateProductionServices = true,
        )
    }

    @Test
    fun expiredToken_forcesOneRefreshAndRetries() = runTest {
        val auth = SuccessfulAuthSession()
        val transport = QueueTransport(
            mutableListOf(
                BackendHttpResponse(
                    403,
                    """{"success":false,"error":{"code":"UNAUTHENTICATED","message":"Expired"}}""",
                ),
                BackendHttpResponse(200, BackendJsonParserTest.accountEnvelope()),
            ),
        )
        val appCheck = SuccessfulAppCheckSession()
        val client = HttpBackendClient(emulatorEnvironment(), auth, appCheck, transport)

        val result = client.getAccountState()

        assertTrue(result is BackendResult.Success)
        assertEquals(listOf(false, true), auth.forceRefreshCalls)
        assertEquals(listOf(false, false), appCheck.forceRefreshCalls)
    }

    @Test
    fun rejectedAppCheckToken_forcesOneRefreshAndRetries() = runTest {
        val auth = SuccessfulAuthSession()
        val appCheck = SuccessfulAppCheckSession()
        val transport = QueueTransport(
            mutableListOf(
                BackendHttpResponse(
                    403,
                    """{"success":false,"error":{"code":"APP_CHECK_FAILED","message":"Rejected"}}""",
                ),
                BackendHttpResponse(200, BackendJsonParserTest.accountEnvelope()),
            ),
        )
        val client = HttpBackendClient(emulatorEnvironment(), auth, appCheck, transport)

        val result = client.getAccountState()

        assertTrue(result is BackendResult.Success)
        assertEquals(listOf(false, false), auth.forceRefreshCalls)
        assertEquals(listOf(false, true), appCheck.forceRefreshCalls)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun temporaryAppCheckAcquisitionFailure_retriesOnceWithForcedRefresh() = runTest {
        val appCheck = QueueAppCheckSession(
            mutableListOf(
                Result.failure(IllegalStateException("temporarily unavailable")),
                Result.success("refreshed-app-check-token"),
            ),
        )
        val transport = RecordingTransport(
            BackendHttpResponse(200, BackendJsonParserTest.accountEnvelope()),
        )
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            appCheck,
            transport,
        )

        val result = client.getAccountState()

        assertTrue(result is BackendResult.Success)
        assertEquals(listOf(false, true), appCheck.forceRefreshCalls)
        assertEquals("refreshed-app-check-token", transport.requests.single().headers["X-Firebase-AppCheck"])
    }

    @Test
    fun repeatedAppCheckAcquisitionFailure_returnsTypedFailureWithoutSendingRequest() = runTest {
        val appCheck = QueueAppCheckSession(
            mutableListOf(
                Result.failure(IllegalStateException("first failure")),
                Result.failure(IllegalStateException("second failure")),
            ),
        )
        val transport = RecordingTransport(
            BackendHttpResponse(200, BackendJsonParserTest.accountEnvelope()),
        )
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            appCheck,
            transport,
        )

        val result = client.getAccountState()

        assertTrue(result is BackendResult.Failure)
        assertTrue((result as BackendResult.Failure).failure is BackendFailure.AppCheck)
        assertEquals(listOf(false, true), appCheck.forceRefreshCalls)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun interruptedTransport_returnsNetworkFailureWithoutLeakingRequestData() = runTest {
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            DisabledAppCheckSession,
            ThrowingTransport(IOException("connection reset")),
        )

        val result = client.imageToWord(ImageConversionRequest("secret-image", "stable-key"))

        assertTrue(result is BackendResult.Failure)
        assertTrue((result as BackendResult.Failure).failure is BackendFailure.Network)
    }

    @Test
    fun httpServerFailure_preservesStableErrorCode() = runTest {
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            DisabledAppCheckSession,
            RecordingTransport(
                BackendHttpResponse(
                    503,
                    """{"success":false,"error":{"code":"CONVERSION_DISABLED","message":"Temporarily unavailable"}}""",
                ),
            ),
        )

        val result = client.imageToWord(ImageConversionRequest("jpeg", "stable-key"))

        assertTrue(result is BackendResult.Failure)
        val error = ((result as BackendResult.Failure).failure as BackendFailure.Server).error
        assertEquals("CONVERSION_DISABLED", error.code)
        assertEquals(503, error.httpStatus)
    }

    @Test
    fun imageConversion_sendsOnlyImageAndIdempotencyKeyWithAuthentication() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(
                200,
                """{"success":true,"result":"https://example.test/result.docx","conversionId":"c1","credits":{"charged":5,"available":90}}""",
            ),
        )
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            SuccessfulAppCheckSession(),
            transport,
        )

        val result = client.imageToWord(ImageConversionRequest("/9j/AA==", "stable-key"))

        assertTrue(result is BackendResult.Success)
        val request = transport.requests.single()
        assertEquals(
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/imageToWordV2",
            request.url,
        )
        assertEquals("Bearer test-id-token", request.headers["Authorization"])
        assertEquals("test-app-check-token", request.headers["X-Firebase-AppCheck"])
        val body = JSONObject(request.body!!)
        assertEquals(setOf("image", "idempotencyKey"), body.keys().asSequence().toSet())
        assertEquals("/9j/AA==", body.getString("image"))
        assertEquals("stable-key", body.getString("idempotencyKey"))
        assertFalse(request.body.orEmpty().contains("credit", ignoreCase = true))
        assertFalse(request.body.orEmpty().contains("userId"))
        assertFalse(request.body.orEmpty().contains("wallet"))
        assertFalse(request.toString().contains("test-id-token"))
        assertFalse(request.toString().contains("test-app-check-token"))
        assertFalse(request.toString().contains("/9j/AA=="))
    }

    @Test
    fun subscriptionVerification_sendsOnlyProductAndTokenWithAuthentication() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(
                200,
                """{"success":true,"account":${BackendJsonParserTest.accountObject()}}""",
            ),
        )
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            SuccessfulAppCheckSession(),
            transport,
        )

        val result = client.verifySubscriptionPurchase(
            SubscriptionVerificationRequest("imagetoword_1w", "same-private-token"),
        )

        assertTrue(result is BackendResult.Success)
        val request = transport.requests.single()
        assertEquals(
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/verifySubscriptionPurchaseV2",
            request.url,
        )
        assertEquals("Bearer test-id-token", request.headers["Authorization"])
        assertEquals("test-app-check-token", request.headers["X-Firebase-AppCheck"])
        val body = JSONObject(request.body!!)
        assertEquals(setOf("productId", "purchaseToken"), body.keys().asSequence().toSet())
        assertEquals("imagetoword_1w", body.getString("productId"))
        assertEquals("same-private-token", body.getString("purchaseToken"))
        assertFalse(request.body.orEmpty().contains("userId"))
        assertFalse(request.body.orEmpty().contains("credit", ignoreCase = true))
        assertFalse(request.body.orEmpty().contains("basePlan"))
        assertFalse(request.toString().contains("same-private-token"))
    }

    @Test
    fun creditCatalog_usesAuthenticatedGetAndSendsNoClientConfiguration() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(
                200,
                """{"success":true,"products":[{"productId":"test_pack_100","productType":"INAPP","credits":100,"expiryDays":30}]}""",
            ),
        )
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            SuccessfulAppCheckSession(),
            transport,
        )

        assertTrue(client.getCreditProductCatalog() is BackendResult.Success)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals(
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/getCreditProductCatalogV2",
            request.url,
        )
        assertEquals(null, request.body)
    }

    @Test
    fun creditVerification_sendsOnlyCatalogProductAndTokenWithoutLoggingSecrets() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(
                200,
                """{"success":true,"purchase":{"productId":"test_pack_100","status":"CONSUMED","credits":100},"account":${BackendJsonParserTest.accountObject(available = 100)}}""",
            ),
        )
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            SuccessfulAppCheckSession(),
            transport,
        )

        val result = client.verifyCreditPurchase(
            CreditPurchaseVerificationRequest("test_pack_100", "private-consumable-token"),
        )

        assertTrue(result is BackendResult.Success)
        val request = transport.requests.single()
        assertEquals(
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/verifyCreditPurchaseV2",
            request.url,
        )
        val body = JSONObject(request.body!!)
        assertEquals(setOf("productId", "purchaseToken"), body.keys().asSequence().toSet())
        assertFalse(request.body.orEmpty().contains("userId"))
        assertFalse(request.body.orEmpty().contains("credits"))
        assertFalse(request.body.orEmpty().contains("expiry"))
        assertFalse(request.toString().contains("private-consumable-token"))
    }

    @Test
    fun creditRecovery_sendsOnlyTokenToTokenOnlyRecoveryEndpoint() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(
                200,
                """{"success":true,"purchase":{"productId":"test_pack_100","status":"CONSUMED","credits":100},"account":${BackendJsonParserTest.accountObject(available = 100)}}""",
            ),
        )
        val client = HttpBackendClient(
            emulatorEnvironment(),
            SuccessfulAuthSession(),
            SuccessfulAppCheckSession(),
            transport,
        )

        assertTrue(client.recoverCreditPurchase("recovery-token") is BackendResult.Success)
        val request = transport.requests.single()
        assertEquals(
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/recoverCreditPurchaseV2",
            request.url,
        )
        val body = JSONObject(request.body!!)
        assertEquals(setOf("purchaseToken"), body.keys().asSequence().toSet())
        assertFalse(request.toString().contains("recovery-token"))
    }

    @Test
    fun disabledProductionV2_neverAcquiresTokensOrSendsRequest() = runTest {
        val auth = SuccessfulAuthSession()
        val appCheck = SuccessfulAppCheckSession()
        val transport = RecordingTransport(
            BackendHttpResponse(200, BackendJsonParserTest.accountEnvelope()),
        )
        val client = HttpBackendClient(disabledProductionEnvironment(), auth, appCheck, transport)

        val result = client.getAccountState()

        assertTrue(result is BackendResult.Failure)
        assertTrue((result as BackendResult.Failure).failure is BackendFailure.IntegrationDisabled)
        assertTrue(auth.forceRefreshCalls.isEmpty())
        assertTrue(appCheck.forceRefreshCalls.isEmpty())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun appCheckProviderPolicy_isolatedByEnvironment() {
        assertEquals(
            AppCheckProviderMode.DISABLED,
            AppCheckEnvironmentPolicy.providerMode(emulatorEnvironment()),
        )
        assertEquals(
            AppCheckProviderMode.PLAY_INTEGRITY,
            AppCheckEnvironmentPolicy.providerMode(disabledProductionEnvironment()),
        )
    }

    private fun emulatorEnvironment() = BackendEnvironment(
        name = BackendEnvironment.EMULATOR,
        accountIntegrationEnabled = true,
        firebaseProjectId = BackendEnvironment.DEMO_PROJECT_ID,
        functionsBaseUrl = BackendEnvironment.DEMO_FUNCTIONS_BASE_URL,
        authEmulatorHost = BackendEnvironment.DEMO_AUTH_HOST,
        authEmulatorPort = BackendEnvironment.DEMO_AUTH_PORT,
        isolateProductionServices = true,
        firebaseAppId = BackendEnvironment.DEMO_FIREBASE_APP_ID,
    )

    private fun disabledProductionEnvironment() = BackendEnvironment(
        name = "production-disabled",
        accountIntegrationEnabled = false,
        firebaseProjectId = "converter-api-project",
        functionsBaseUrl = "",
        authEmulatorHost = null,
        authEmulatorPort = null,
        isolateProductionServices = false,
    )
}

private class SuccessfulAuthSession : AuthSession {
    val forceRefreshCalls = mutableListOf<Boolean>()

    override suspend fun ensureAuthenticated(): Result<Unit> = Result.success(Unit)

    override suspend fun idToken(forceRefresh: Boolean): Result<String> {
        forceRefreshCalls += forceRefresh
        return Result.success("test-id-token")
    }
}

private class SuccessfulAppCheckSession : AppCheckSession {
    val forceRefreshCalls = mutableListOf<Boolean>()

    override suspend fun token(forceRefresh: Boolean): Result<String?> {
        forceRefreshCalls += forceRefresh
        return Result.success("test-app-check-token")
    }
}

private class QueueAppCheckSession(
    private val results: MutableList<Result<String?>>,
) : AppCheckSession {
    val forceRefreshCalls = mutableListOf<Boolean>()

    override suspend fun token(forceRefresh: Boolean): Result<String?> {
        forceRefreshCalls += forceRefresh
        return results.removeAt(0)
    }
}

private class RecordingTransport(
    private val response: BackendHttpResponse,
) : BackendHttpTransport {
    val requests = mutableListOf<BackendHttpRequest>()

    override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse {
        requests += request
        return response
    }
}

private class QueueTransport(
    private val responses: MutableList<BackendHttpResponse>,
) : BackendHttpTransport {
    val requests = mutableListOf<BackendHttpRequest>()

    override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse {
        requests += request
        return responses.removeAt(0)
    }
}

private class ThrowingTransport(
    private val error: IOException,
) : BackendHttpTransport {
    override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse = throw error
}
