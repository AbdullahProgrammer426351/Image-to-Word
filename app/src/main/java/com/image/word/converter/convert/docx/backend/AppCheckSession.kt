package com.image.word.converter.convert.docx.backend

import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.tasks.await

interface AppCheckSession {
    suspend fun token(forceRefresh: Boolean = false): Result<String?>
}

class FirebaseAppCheckSession(
    private val firebaseAppCheck: FirebaseAppCheck,
) : AppCheckSession {
    override suspend fun token(forceRefresh: Boolean): Result<String?> = runCatching {
        firebaseAppCheck.getAppCheckToken(forceRefresh).await().token.also { token ->
            check(token.isNotBlank()) { "Firebase App Check returned an empty token" }
        }
    }
}

data object DisabledAppCheckSession : AppCheckSession {
    override suspend fun token(forceRefresh: Boolean): Result<String?> = Result.success(null)
}

enum class AppCheckProviderMode {
    DISABLED,
    PLAY_INTEGRITY,
}

object AppCheckEnvironmentPolicy {
    fun providerMode(environment: BackendEnvironment): AppCheckProviderMode =
        if (environment.isEmulator) {
            AppCheckProviderMode.DISABLED
        } else {
            AppCheckProviderMode.PLAY_INTEGRITY
        }
}
