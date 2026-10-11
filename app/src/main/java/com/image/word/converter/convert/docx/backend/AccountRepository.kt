package com.image.word.converter.convert.docx.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AccountRepositoryState {
    data object Disabled : AccountRepositoryState
    data class Loading(val previous: AccountState? = null) : AccountRepositoryState
    data class Ready(val account: AccountState) : AccountRepositoryState
    data class Degraded(
        val previous: AccountState?,
        val failure: BackendFailure,
    ) : AccountRepositoryState
}

class AccountRepository(
    private val integrationEnabled: Boolean,
    private val authSession: AuthSession,
    private val backendClient: BackendClient,
) {
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow<AccountRepositoryState>(
        if (integrationEnabled) AccountRepositoryState.Loading() else AccountRepositoryState.Disabled,
    )
    val state: StateFlow<AccountRepositoryState> = _state.asStateFlow()

    suspend fun initialize() = operationMutex.withLock {
        if (!integrationEnabled) return@withLock
        val previous = currentAccount()
        _state.value = AccountRepositoryState.Loading(previous)
        val authResult = authSession.ensureAuthenticated()
        if (authResult.isFailure) {
            _state.value = AccountRepositoryState.Degraded(
                previous = previous,
                failure = BackendFailure.Authentication(authResult.exceptionOrNull()),
            )
            return@withLock
        }

        // bootstrapUserV2 already returns the complete authoritative account projection.
        applyResult(backendClient.bootstrapUser(), previous)
    }

    suspend fun refresh() = operationMutex.withLock {
        if (!integrationEnabled) return@withLock
        val previous = currentAccount()
        _state.value = AccountRepositoryState.Loading(previous)
        val authResult = authSession.ensureAuthenticated()
        if (authResult.isFailure) {
            _state.value = AccountRepositoryState.Degraded(
                previous = previous,
                failure = BackendFailure.Authentication(authResult.exceptionOrNull()),
            )
            return@withLock
        }

        when (val result = backendClient.getAccountState()) {
            is BackendResult.Success -> _state.value = AccountRepositoryState.Ready(result.value)
            is BackendResult.Failure -> {
                val serverFailure = result.failure as? BackendFailure.Server
                if (serverFailure?.error?.httpStatus == 404 &&
                    serverFailure.error.code == "ACCOUNT_NOT_FOUND"
                ) {
                    applyResult(backendClient.bootstrapUser(), previous)
                } else {
                    _state.value = AccountRepositoryState.Degraded(previous, result.failure)
                }
            }
        }
    }

    private fun applyResult(result: BackendResult<AccountState>, previous: AccountState?) {
        _state.value = when (result) {
            is BackendResult.Success -> AccountRepositoryState.Ready(result.value)
            is BackendResult.Failure -> AccountRepositoryState.Degraded(previous, result.failure)
        }
    }

    private fun currentAccount(): AccountState? = when (val current = _state.value) {
        is AccountRepositoryState.Ready -> current.account
        is AccountRepositoryState.Loading -> current.previous
        is AccountRepositoryState.Degraded -> current.previous
        AccountRepositoryState.Disabled -> null
    }
}

object BackendAccountStore {
    lateinit var repository: AccountRepository
        internal set
}
