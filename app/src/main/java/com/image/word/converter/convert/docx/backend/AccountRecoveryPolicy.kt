package com.image.word.converter.convert.docx.backend

data class AccountIdentitySnapshot(
    val hasUser: Boolean,
    val isAnonymous: Boolean,
    val providerIds: Set<String>,
)

enum class PurchaseRecoveryReadiness {
    RECOVERABLE,
    SIGN_IN_REQUIRED,
    LOADING,
}

object AccountRecoveryPolicy {
    fun readiness(identity: AccountIdentitySnapshot): PurchaseRecoveryReadiness = when {
        !identity.hasUser -> PurchaseRecoveryReadiness.LOADING
        identity.isAnonymous -> PurchaseRecoveryReadiness.SIGN_IN_REQUIRED
        identity.providerIds.isEmpty() -> PurchaseRecoveryReadiness.SIGN_IN_REQUIRED
        else -> PurchaseRecoveryReadiness.RECOVERABLE
    }
}

object AccountIdentityStore {
    var snapshotProvider: () -> AccountIdentitySnapshot = {
        AccountIdentitySnapshot(hasUser = false, isAnonymous = true, providerIds = emptySet())
    }
}
