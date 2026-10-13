package com.image.word.converter.convert.docx.backend

import org.json.JSONObject
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
        val client = HttpBackendClient(emulatorEnvironment(), SuccessfulAuthSession(), transport)

        val result = client.bootstrapUser()

        assertTrue(result is BackendResult.Success)
        val request = transport.requests.single()
        assertEquals("Bearer test-id-token", request.headers["Authorization"])
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
        val client = HttpBackendClient(environment, SuccessfulAuthSession(), transport)

        client.getAccountState()

        val url = transport.requests.single().url
        assertEquals(
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/getAccountStateV2",
            url,
        )
        assertFalse(url.contains("converter-api-project"))
        assertFalse(url.startsWith("https://"))
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
        val client = HttpBackendClient(emulatorEnvironment(), auth, transport)

        val result = client.getAccountState()

        assertTrue(result is BackendResult.Success)
        assertEquals(listOf(false, true), auth.forceRefreshCalls)
    }

    @Test
    fun imageConversion_sendsOnlyImageAndIdempotencyKeyWithAuthentication() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(
                200,
                """{"success":true,"result":"https://example.test/result.docx","conversionId":"c1","credits":{"charged":5,"available":90}}""",
            ),
        )
        val client = HttpBackendClient(emulatorEnvironment(), SuccessfulAuthSession(), transport)

        val result = client.imageToWord(ImageConversionRequest("/9j/AA==", "stable-key"))

        assertTrue(result is BackendResult.Success)
        val request = transport.requests.single()
        assertEquals(
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/imageToWordV2",
            request.url,
        )
        assertEquals("Bearer test-id-token", request.headers["Authorization"])
        val body = JSONObject(request.body!!)
        assertEquals(setOf("image", "idempotencyKey"), body.keys().asSequence().toSet())
        assertEquals("/9j/AA==", body.getString("image"))
        assertEquals("stable-key", body.getString("idempotencyKey"))
        assertFalse(request.body.orEmpty().contains("credit", ignoreCase = true))
        assertFalse(request.body.orEmpty().contains("userId"))
        assertFalse(request.body.orEmpty().contains("wallet"))
    }

    @Test
    fun subscriptionVerification_sendsOnlyProductAndTokenWithAuthentication() = runTest {
        val transport = RecordingTransport(
            BackendHttpResponse(
                200,
                """{"success":true,"account":${BackendJsonParserTest.accountObject()}}""",
            ),
        )
        val client = HttpBackendClient(emulatorEnvironment(), SuccessfulAuthSession(), transport)

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
        val body = JSONObject(request.body!!)
        assertEquals(setOf("productId", "purchaseToken"), body.keys().asSequence().toSet())
        assertEquals("imagetoword_1w", body.getString("productId"))
        assertEquals("same-private-token", body.getString("purchaseToken"))
        assertFalse(request.body.orEmpty().contains("userId"))
        assertFalse(request.body.orEmpty().contains("credit", ignoreCase = true))
        assertFalse(request.body.orEmpty().contains("basePlan"))
    }

    private fun emulatorEnvironment() = BackendEnvironment(
        name = BackendEnvironment.EMULATOR,
        accountIntegrationEnabled = true,
        firebaseProjectId = BackendEnvironment.DEMO_PROJECT_ID,
        functionsBaseUrl = BackendEnvironment.DEMO_FUNCTIONS_BASE_URL,
        authEmulatorHost = BackendEnvironment.DEMO_AUTH_HOST,
        authEmulatorPort = BackendEnvironment.DEMO_AUTH_PORT,
        isolateProductionServices = true,
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
    override suspend fun execute(request: BackendHttpRequest): BackendHttpResponse =
        responses.removeAt(0)
}
