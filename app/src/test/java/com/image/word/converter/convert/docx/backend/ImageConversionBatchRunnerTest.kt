package com.image.word.converter.convert.docx.backend

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageConversionBatchRunnerTest {
    @Test
    fun requestFactory_assignsIndependentStableKeysToEachImage() {
        val keys = ArrayDeque(listOf("key-a", "key-b"))

        val prepared = ImageConversionRequestFactory { keys.removeFirst() }
            .prepare(listOf("jpeg-a", "jpeg-b"))

        assertEquals(listOf("key-a", "key-b"), prepared.map { it.request.idempotencyKey })
        assertNotEquals(prepared[0].request.idempotencyKey, prepared[1].request.idempotencyKey)
        assertEquals(listOf("jpeg-a", "jpeg-b"), prepared.map { it.request.image })
    }

    @Test
    fun ambiguousNetworkRetry_reusesTheExactSameRequestAndKey() = runTest {
        val client = RecordingConversionClient(
            ArrayDeque(
                listOf(
                    BackendResult.Failure(BackendFailure.Network(IOException("timeout"))),
                    successfulConversion(),
                ),
            ),
        )
        val prepared = PreparedImageConversion(
            index = 0,
            request = ImageConversionRequest("jpeg-payload", "stable-key"),
        )
        val runner = ImageConversionBatchRunner(client, retryDelay = {})

        val result = runner.run(listOf(prepared)).items.single()

        assertTrue(result.result is BackendResult.Success)
        assertEquals(2, result.requestAttempts)
        assertEquals(listOf("stable-key", "stable-key"), client.requests.map { it.idempotencyKey })
        assertEquals(listOf("jpeg-payload", "jpeg-payload"), client.requests.map { it.image })
        assertSame(client.requests[0], client.requests[1])
    }

    @Test
    fun insufficientCredits_isTerminalAndDoesNotRetry() = runTest {
        val client = RecordingConversionClient(
            ArrayDeque(
                listOf(
                    BackendResult.Failure(
                        BackendFailure.Server(
                            BackendError(
                                code = "INSUFFICIENT_CREDITS",
                                message = "Insufficient credits",
                                httpStatus = 402,
                                requiredCredits = 5,
                                availableCredits = 2,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val runner = ImageConversionBatchRunner(client, retryDelay = {})

        val result = runner.run(
            listOf(PreparedImageConversion(0, ImageConversionRequest("jpeg", "key"))),
        ).items.single()

        assertTrue(result.result is BackendResult.Failure)
        assertEquals(1, result.requestAttempts)
    }

    @Test
    fun successfulConversion_doesNotDeductCachedCreditsBeforeAuthoritativeRefresh() = runTest {
        val client = RecordingConversionClient(
            responses = ArrayDeque(listOf(successfulConversion())),
            bootstrapAccount = sampleAccount(available = 95),
            refreshedAccount = sampleAccount(available = 90),
        )
        val repository = AccountRepository(
            integrationEnabled = true,
            authSession = TestAuthSession,
            backendClient = client,
        )
        repository.initialize()

        ImageConversionBatchRunner(client, retryDelay = {}).run(
            listOf(PreparedImageConversion(0, ImageConversionRequest("jpeg", "key"))),
        )

        assertEquals(
            95L,
            (repository.state.value as AccountRepositoryState.Ready).account.credits.available,
        )
        repository.refresh()
        assertEquals(
            90L,
            (repository.state.value as AccountRepositoryState.Ready).account.credits.available,
        )
    }

    @Test
    fun v2AccessNeverUsesLocalSubscriptionOrAttemptCountersAsAuthority() {
        assertTrue(
            ConversionAccessPolicy.canStart(
                mode = ConversionBackendMode.V2,
                isLocallySubscribed = false,
                remainingLocalAttempts = 0,
            ),
        )
        assertTrue(
            ConversionAccessPolicy.canStart(
                mode = ConversionBackendMode.V2,
                isLocallySubscribed = true,
                remainingLocalAttempts = 0,
            ),
        )
        assertEquals(
            ConversionBackendMode.LEGACY,
            ConversionAccessPolicy.backendMode(disabledEnvironment()),
        )
    }

    private fun successfulConversion(): BackendResult<ImageConversionResponse> =
        BackendResult.Success(
            ImageConversionResponse(
                resultUrl = "https://example.test/result.docx",
                conversionId = "conversion-1",
                credits = ConversionCredits(charged = 5, available = 90),
            ),
        )

    private fun sampleAccount(available: Long) = AccountState(
        user = BackendUser("user", "Guest", null, "ANONYMOUS"),
        credits = CreditsState(available, 0, null),
        subscription = SubscriptionState(null, null, null, null, null),
    )

    private fun disabledEnvironment() = BackendEnvironment(
        name = "production-disabled",
        accountIntegrationEnabled = false,
        firebaseProjectId = "converter-api-project",
        functionsBaseUrl = "",
        authEmulatorHost = null,
        authEmulatorPort = null,
        isolateProductionServices = false,
    )
}

private class RecordingConversionClient(
    private val responses: ArrayDeque<BackendResult<ImageConversionResponse>>,
    private val bootstrapAccount: AccountState? = null,
    private val refreshedAccount: AccountState? = null,
) : BackendClient {
    val requests = mutableListOf<ImageConversionRequest>()

    override suspend fun bootstrapUser(): BackendResult<AccountState> =
        bootstrapAccount?.let { BackendResult.Success(it) }
            ?: BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun getAccountState(): BackendResult<AccountState> =
        refreshedAccount?.let { BackendResult.Success(it) }
            ?: BackendResult.Failure(BackendFailure.IntegrationDisabled)

    override suspend fun imageToWord(
        request: ImageConversionRequest,
    ): BackendResult<ImageConversionResponse> {
        requests += request
        return responses.removeFirst()
    }

    override suspend fun verifySubscriptionPurchase(
        request: SubscriptionVerificationRequest,
    ): BackendResult<AccountState> =
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
}

private data object TestAuthSession : AuthSession {
    override suspend fun ensureAuthenticated(): Result<Unit> = Result.success(Unit)
    override suspend fun idToken(forceRefresh: Boolean): Result<String> = Result.success("token")
}
