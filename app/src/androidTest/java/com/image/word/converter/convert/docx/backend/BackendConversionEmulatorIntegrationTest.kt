package com.image.word.converter.convert.docx.backend

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.image.word.converter.convert.docx.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendConversionEmulatorIntegrationTest {
    @Test
    fun smallJpeg_sameIdempotencyKey_chargesCreditsOnce() = runBlocking {
        assertTrue(BuildConfig.DEBUG)
        val environment = BackendEnvironment.current()
        assertTrue(environment.isEmulator)
        assertEquals(BackendEnvironment.DEMO_PROJECT_ID, environment.firebaseProjectId)

        val repository = BackendAccountStore.repository
        resetLocalAuthSessions()
        withTimeout(30_000) { repository.initialize() }
        val initialBalance = repository.readyAccount().credits.available
        val request = ImageConversionRequest(
            image = TINY_JPEG_BASE64,
            idempotencyKey = STABLE_IDEMPOTENCY_KEY,
        )

        val first = withTimeout(120_000) {
            BackendClientStore.client.imageToWord(request).requireSuccess()
        }
        assertTrue(first.resultUrl.isNotBlank())
        assertTrue(first.conversionId.isNotBlank())
        assertEquals(MINIMUM_CHARGE, first.credits.charged)
        assertEquals(initialBalance - MINIMUM_CHARGE, first.credits.available)

        withTimeout(30_000) { repository.refresh() }
        val balanceAfterFirstRequest = repository.readyAccount().credits.available
        assertEquals(initialBalance - MINIMUM_CHARGE, balanceAfterFirstRequest)

        val repeated = withTimeout(120_000) {
            BackendClientStore.client.imageToWord(request).requireSuccess()
        }
        assertEquals(first.conversionId, repeated.conversionId)
        assertEquals(MINIMUM_CHARGE, repeated.credits.charged)
        assertEquals(balanceAfterFirstRequest, repeated.credits.available)

        withTimeout(30_000) { repository.refresh() }
        assertEquals(balanceAfterFirstRequest, repository.readyAccount().credits.available)
    }

    private fun AccountRepository.readyAccount(): AccountState {
        val ready = state.value as? AccountRepositoryState.Ready
        assertTrue("Expected authoritative account state, got ${state.value}", ready != null)
        return ready!!.account
    }

    private fun BackendResult<ImageConversionResponse>.requireSuccess(): ImageConversionResponse {
        if (this is BackendResult.Success) return value
        throw AssertionError("Expected successful conversion, got $this")
    }

    private fun resetLocalAuthSessions() {
        FirebaseApp.getApps(InstrumentationRegistry.getInstrumentation().targetContext)
            .forEach { FirebaseAuth.getInstance(it).signOut() }
    }

    private companion object {
        const val MINIMUM_CHARGE = 5L
        const val STABLE_IDEMPOTENCY_KEY = "android-physical-device-conversion-v1"
        const val TINY_JPEG_BASE64 =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABD/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/EB//xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/EB//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/EB//2Q=="
    }
}
