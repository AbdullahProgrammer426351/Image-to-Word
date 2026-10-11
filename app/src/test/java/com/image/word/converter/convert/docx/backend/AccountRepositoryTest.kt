package com.image.word.converter.convert.docx.backend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initialize_transitionsLoadingToReady() = runTest {
        val response = CompletableDeferred<BackendResult<AccountState>>()
        val repository = AccountRepository(
            integrationEnabled = true,
            authSession = FakeAuthSession(),
            backendClient = FakeBackendClient(bootstrapResponse = { response.await() }),
        )

        val job = launch { repository.initialize() }
        runCurrent()
        assertTrue(repository.state.value is AccountRepositoryState.Loading)

        response.complete(BackendResult.Success(sampleAccount()))
        job.join()

        val ready = repository.state.value as AccountRepositoryState.Ready
        assertEquals(95L, ready.account.credits.available)
    }

    @Test
    fun authFailure_isRepresentedAsDegradedAuthentication() = runTest {
        val repository = AccountRepository(
            integrationEnabled = true,
            authSession = FakeAuthSession(authResult = Result.failure(IllegalStateException("auth failed"))),
            backendClient = FakeBackendClient(),
        )

        repository.initialize()

        val degraded = repository.state.value as AccountRepositoryState.Degraded
        assertTrue(degraded.failure is BackendFailure.Authentication)
    }

    @Test
    fun backendFailure_producesDegradedState() = runTest {
        val repository = AccountRepository(
            integrationEnabled = true,
            authSession = FakeAuthSession(),
            backendClient = FakeBackendClient(
                bootstrapResponse = {
                    BackendResult.Failure(
                        BackendFailure.Server(BackendError("INTERNAL_ERROR", "Unavailable", 500)),
                    )
                },
            ),
        )

        repository.initialize()

        val degraded = repository.state.value as AccountRepositoryState.Degraded
        assertTrue(degraded.failure is BackendFailure.Server)
    }

    private fun sampleAccount() = AccountState(
        user = BackendUser("backend-user", "Guest", null, "ANONYMOUS"),
        credits = CreditsState(95, 0, "2026-10-17T12:00:00.000Z"),
        subscription = SubscriptionState(null, null, null, null, null),
    )
}

private class FakeAuthSession(
    private val authResult: Result<Unit> = Result.success(Unit),
) : AuthSession {
    override suspend fun ensureAuthenticated(): Result<Unit> = authResult
    override suspend fun idToken(forceRefresh: Boolean): Result<String> = Result.success("token")
}

private class FakeBackendClient(
    private val bootstrapResponse: suspend () -> BackendResult<AccountState> = {
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
    },
    private val accountResponse: suspend () -> BackendResult<AccountState> = {
        BackendResult.Failure(BackendFailure.IntegrationDisabled)
    },
) : BackendClient {
    override suspend fun bootstrapUser(): BackendResult<AccountState> = bootstrapResponse()
    override suspend fun getAccountState(): BackendResult<AccountState> = accountResponse()
}
