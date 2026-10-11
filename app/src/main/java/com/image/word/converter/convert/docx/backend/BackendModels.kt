package com.image.word.converter.convert.docx.backend

data class BackendUser(
    val id: String,
    val userName: String,
    val email: String?,
    val accountType: String,
)

data class CreditsState(
    val available: Long,
    val reserved: Long,
    val nextExpiryAt: String?,
)

data class SubscriptionState(
    val plan: String?,
    val status: String?,
    val periodEnd: String?,
    val autoRenewing: Boolean?,
    val acknowledgementState: String?,
)

data class AccountState(
    val user: BackendUser,
    val credits: CreditsState,
    val subscription: SubscriptionState,
)

data class BackendError(
    val code: String,
    val message: String,
    val httpStatus: Int,
)

sealed interface BackendResult<out T> {
    data class Success<T>(val value: T) : BackendResult<T>
    data class Failure(val failure: BackendFailure) : BackendResult<Nothing>
}

sealed interface BackendFailure {
    data class Authentication(val cause: Throwable? = null) : BackendFailure
    data class Network(val cause: Throwable) : BackendFailure
    data class Server(val error: BackendError) : BackendFailure
    data class InvalidResponse(val cause: Throwable? = null) : BackendFailure
    data object IntegrationDisabled : BackendFailure
}
