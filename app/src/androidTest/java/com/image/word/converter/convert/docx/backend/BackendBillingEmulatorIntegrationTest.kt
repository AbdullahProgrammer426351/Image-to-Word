package com.image.word.converter.convert.docx.backend

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.image.word.converter.convert.docx.subscriptions.AuthoritativeAccountGateway
import com.image.word.converter.convert.docx.subscriptions.BackendSubscriptionVerifier
import com.image.word.converter.convert.docx.subscriptions.PendingPurchaseStore
import com.image.word.converter.convert.docx.subscriptions.PlayPurchaseCandidate
import com.image.word.converter.convert.docx.subscriptions.PlayPurchaseState
import com.image.word.converter.convert.docx.subscriptions.PrivatePendingPurchaseStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendBillingEmulatorIntegrationTest {
    @Test
    fun fakeWeeklyPurchase_isVerifiedOnceAndRefreshesCredits() = runBlocking {
        verifyFixture(
            productId = "imagetoword_1w",
            purchaseTokenArgument = "fakePlayWeeklyPurchaseToken",
            expectedCredits = 500,
        )
    }

    @Test
    fun fakeMonthlyPurchase_isVerifiedOnceAndRefreshesCredits() = runBlocking {
        verifyFixture(
            productId = "imagetoword_1m",
            purchaseTokenArgument = "fakePlayMonthlyPurchaseToken",
            expectedCredits = 1_200,
        )
    }

    @Test
    fun persistedPurchase_survivesVerifierRecreationAndCorruptSiblingRecord() = runBlocking {
        val purchaseToken = requiredArgument("fakePlayRecoveryPurchaseToken")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("pending_subscription_verification", 0)
            .edit()
            .putString(
                "requests",
                """[{"broken":true},{"productId":"imagetoword_1w","purchaseToken":"$purchaseToken"}]""",
            )
            .commit()
        val store = PrivatePendingPurchaseStore(context)
        assertEquals(listOf(purchaseToken), store.all().map { it.purchaseToken })

        val repository = initializeRepository()
        val verifier = verifier(repository, store)
        val recovered = withTimeout(60_000) { verifier.retryPersisted() }

        assertTrue(recovered.hasVerifiedPurchase)
        assertTrue(store.all().isEmpty())
        assertEquals(500L, repository.readyAccount().credits.available)
        assertEquals(ConversionAccessMode.CREDIT_BASED, repository.readyAccount().conversionAccess.mode)
    }

    private suspend fun verifyFixture(
        productId: String,
        purchaseTokenArgument: String,
        expectedCredits: Long,
    ) {
        val purchaseToken = requiredArgument(purchaseTokenArgument)
        val repository = initializeRepository()
        val store = InstrumentationPendingStore()
        val verifier = verifier(repository, store)
        val candidate = PlayPurchaseCandidate(
            productId = productId,
            purchaseToken = purchaseToken,
            state = PlayPurchaseState.PURCHASED,
        )

        val first = withTimeout(60_000) { verifier.process(listOf(candidate, candidate)) }
        assertTrue(first.hasVerifiedPurchase)
        assertTrue(store.all().isEmpty())
        assertEquals(expectedCredits, repository.readyAccount().credits.available)
        assertEquals(ConversionAccessMode.CREDIT_BASED, repository.readyAccount().conversionAccess.mode)

        val repeated = withTimeout(60_000) { verifier.process(listOf(candidate)) }
        assertTrue(repeated.hasVerifiedPurchase)
        assertTrue(store.all().isEmpty())
        assertEquals(expectedCredits, repository.readyAccount().credits.available)
    }

    private fun verifier(
        repository: AccountRepository,
        store: PendingPurchaseStore,
    ) = BackendSubscriptionVerifier(
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

    private suspend fun initializeRepository(): AccountRepository {
        val environment = BackendEnvironment.current()
        assertTrue(environment.isEmulator)
        assertEquals(BackendEnvironment.DEMO_PROJECT_ID, environment.firebaseProjectId)
        FirebaseApp.getApps(InstrumentationRegistry.getInstrumentation().targetContext)
            .forEach { FirebaseAuth.getInstance(it).signOut() }
        val repository = BackendAccountStore.repository
        withTimeout(30_000) { repository.initialize() }
        assertTrue(repository.state.value is AccountRepositoryState.Ready)
        return repository
    }

    private fun AccountRepository.readyAccount(): AccountState {
        val ready = state.value as? AccountRepositoryState.Ready
        assertTrue("Expected authoritative account state, got ${state.value}", ready != null)
        return ready!!.account
    }

    private fun requiredArgument(name: String): String {
        val value = InstrumentationRegistry.getArguments().getString(name)
        assertTrue("Missing required fake fixture argument $name", !value.isNullOrBlank())
        return value!!
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
