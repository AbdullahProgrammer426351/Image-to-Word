package com.image.word.converter.convert.docx.backend

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

interface AuthSession {
    suspend fun ensureAuthenticated(): Result<Unit>
    suspend fun idToken(forceRefresh: Boolean = false): Result<String>
}

class FirebaseAnonymousAuthSession(
    private val firebaseAuth: FirebaseAuth,
) : AuthSession {
    override suspend fun ensureAuthenticated(): Result<Unit> = runCatching {
        if (firebaseAuth.currentUser == null) {
            val user = firebaseAuth.signInAnonymously().await().user
            checkNotNull(user) { "Firebase anonymous authentication returned no user" }
        }
    }

    override suspend fun idToken(forceRefresh: Boolean): Result<String> = runCatching {
        ensureAuthenticated().getOrThrow()
        val user = checkNotNull(firebaseAuth.currentUser) { "Firebase user is unavailable" }
        checkNotNull(user.getIdToken(forceRefresh).await().token) {
            "Firebase ID token is unavailable"
        }
    }
}
