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
    fun smallJpeg_freeThenCreditBasedRequestsRemainIdempotent() = runBlocking {
        assertTrue(BuildConfig.DEBUG)
        val environment = BackendEnvironment.current()
        assertTrue(environment.isEmulator)
        assertEquals(BackendEnvironment.DEMO_PROJECT_ID, environment.firebaseProjectId)

        val repository = BackendAccountStore.repository
        resetLocalAuthSessions()
        withTimeout(30_000) { repository.initialize() }
        val initialAccount = repository.readyAccount()
        assertEquals(ConversionAccessMode.FREE, initialAccount.conversionAccess.mode)
        val freeRequest = ImageConversionRequest(
            image = TINY_JPEG_BASE64,
            idempotencyKey = FREE_IDEMPOTENCY_KEY,
        )

        val free = withTimeout(120_000) {
            BackendClientStore.client.imageToWord(freeRequest).requireSuccess()
        }
        assertTrue(free.resultUrl.isNotBlank())
        assertTrue(free.conversionId.isNotBlank())
        assertEquals(0L, free.credits.charged)
        assertEquals(initialAccount.credits.available, free.credits.available)

        withTimeout(30_000) { repository.refresh() }
        assertEquals(initialAccount.credits.available, repository.readyAccount().credits.available)
        assertEquals(ConversionAccessMode.NONE, repository.readyAccount().conversionAccess.mode)

        val repeatedFree = withTimeout(120_000) {
            BackendClientStore.client.imageToWord(freeRequest).requireSuccess()
        }
        assertEquals(free.conversionId, repeatedFree.conversionId)
        assertEquals(0L, repeatedFree.credits.charged)
        assertEquals(initialAccount.credits.available, repeatedFree.credits.available)

        val purchaseToken = InstrumentationRegistry.getArguments()
            .getString("fakePlayConversionPurchaseToken")
        assertTrue("Missing fake conversion-credit fixture", !purchaseToken.isNullOrBlank())
        val purchaseAccount = withTimeout(60_000) {
            BackendClientStore.client.verifySubscriptionPurchase(
                SubscriptionVerificationRequest("imagetoword_1w", purchaseToken!!),
            ).requireAccountSuccess()
        }
        repository.acceptAuthoritativeAccount(purchaseAccount)
        withTimeout(30_000) { repository.refresh() }
        assertEquals(500L, repository.readyAccount().credits.available)
        assertEquals(ConversionAccessMode.CREDIT_BASED, repository.readyAccount().conversionAccess.mode)

        val chargedRequest = ImageConversionRequest(
            image = TINY_JPEG_BASE64,
            idempotencyKey = CREDIT_IDEMPOTENCY_KEY,
        )
        val charged = withTimeout(120_000) {
            BackendClientStore.client.imageToWord(chargedRequest).requireSuccess()
        }
        assertEquals(MINIMUM_CHARGE, charged.credits.charged)
        assertEquals(500L - MINIMUM_CHARGE, charged.credits.available)

        val repeatedCharged = withTimeout(120_000) {
            BackendClientStore.client.imageToWord(chargedRequest).requireSuccess()
        }
        assertEquals(charged.conversionId, repeatedCharged.conversionId)
        assertEquals(MINIMUM_CHARGE, repeatedCharged.credits.charged)
        assertEquals(charged.credits.available, repeatedCharged.credits.available)

        withTimeout(30_000) { repository.refresh() }
        assertEquals(500L - MINIMUM_CHARGE, repository.readyAccount().credits.available)
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

    private fun BackendResult<AccountState>.requireAccountSuccess(): AccountState {
        if (this is BackendResult.Success) return value
        throw AssertionError("Expected successful purchase verification, got $this")
    }

    private fun resetLocalAuthSessions() {
        FirebaseApp.getApps(InstrumentationRegistry.getInstrumentation().targetContext)
            .forEach { FirebaseAuth.getInstance(it).signOut() }
    }

    private companion object {
        const val MINIMUM_CHARGE = 5L
        const val FREE_IDEMPOTENCY_KEY = "android-physical-device-free-conversion-v1"
        const val CREDIT_IDEMPOTENCY_KEY = "android-physical-device-credit-conversion-v1"
        const val TINY_JPEG_BASE64 =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABD/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/EB//xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/EB//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/EB//2Q=="
    }
}
