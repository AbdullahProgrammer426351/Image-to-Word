package com.image.word.converter.convert.docx.backend

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.image.word.converter.convert.docx.subscriptions.AuthoritativeAccountGateway
import com.image.word.converter.convert.docx.subscriptions.BackendSubscriptionVerifier
import com.image.word.converter.convert.docx.subscriptions.PendingPurchaseStore
import com.image.word.converter.convert.docx.subscriptions.PlayPurchaseCandidate
import com.image.word.converter.convert.docx.subscriptions.PlayPurchaseState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendBillingEmulatorIntegrationTest {
    @Test
    fun fakePlayPurchase_isVerifiedByFirebaseEmulator() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val productId = arguments.getString("fakePlayProductId")
        val purchaseToken = arguments.getString("fakePlayPurchaseToken")
        assumeTrue(
            "Seed a fake Play fixture and pass fakePlayProductId/fakePlayPurchaseToken",
            !productId.isNullOrBlank() && !purchaseToken.isNullOrBlank(),
        )

        val environment = BackendEnvironment.current()
        assertTrue(environment.isEmulator)
        assertEquals(BackendEnvironment.DEMO_PROJECT_ID, environment.firebaseProjectId)
        val repository = BackendAccountStore.repository
        withTimeout(30_000) { repository.initialize() }
        val store = InstrumentationPendingStore()
        val verifier = BackendSubscriptionVerifier(
            backendClient = BackendClientStore.client,
            accountGateway = object : AuthoritativeAccountGateway {
                override suspend fun accept(account: AccountState) {
                    repository.acceptAuthoritativeAccount(account)
                }

                override suspend fun refresh() {
                    repository.refresh()
                }
            },
            pendingStore = store,
            retryDelay = {},
        )

        val result = withTimeout(60_000) {
            verifier.process(
                listOf(
                    PlayPurchaseCandidate(
                        productId = productId!!,
                        purchaseToken = purchaseToken!!,
                        state = PlayPurchaseState.PURCHASED,
                    ),
                ),
            )
        }

        assertTrue(result.hasVerifiedPurchase)
        assertTrue(store.all().isEmpty())
        assertTrue(repository.state.value is AccountRepositoryState.Ready)
    }
}

private class InstrumentationPendingStore : PendingPurchaseStore {
    private val requests = linkedMapOf<String, SubscriptionVerificationRequest>()

    override suspend fun upsert(request: SubscriptionVerificationRequest) {
        requests[request.purchaseToken] = request
    }

    override suspend fun remove(purchaseToken: String) {
        requests.remove(purchaseToken)
    }

    override suspend fun all(): List<SubscriptionVerificationRequest> = requests.values.toList()
}
