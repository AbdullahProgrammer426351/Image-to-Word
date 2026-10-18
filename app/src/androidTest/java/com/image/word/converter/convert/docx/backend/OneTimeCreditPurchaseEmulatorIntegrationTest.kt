package com.image.word.converter.convert.docx.backend

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.image.word.converter.convert.docx.subscriptions.AuthoritativeAccountGateway
import com.image.word.converter.convert.docx.subscriptions.CreditPurchaseVerifier
import com.image.word.converter.convert.docx.subscriptions.PlayPurchaseCandidate
import com.image.word.converter.convert.docx.subscriptions.PlayPurchaseState
import com.image.word.converter.convert.docx.subscriptions.PrivatePendingCreditPurchaseStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OneTimeCreditPurchaseEmulatorIntegrationTest {
    @Test
    fun catalog_grantsRepeatRecoveryAndConsumptionRetry_areServerAuthoritative() = runBlocking {
        val catalog = withTimeout(30_000) {
            BackendClientStore.client.getCreditProductCatalog().requireCatalog()
        }
        assertEquals(setOf("test_pack_100", "test_pack_500"), catalog.map { it.productId }.toSet())
        assertTrue(catalog.all { it.productType == "INAPP" && it.credits > 0 && it.expiryDays > 0 })

        val repository = initializeRepository()
        val store = PrivatePendingCreditPurchaseStore(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        store.all().forEach { store.remove(it.purchaseToken) }
        val verifier = verifier(repository, store, catalog.mapTo(linkedSetOf()) { it.productId })

        val firstToken = requiredArgument("fakePlayCredit100Token")
        val first = candidate("test_pack_100", firstToken)
        assertTrue(withTimeout(60_000) { verifier.process(listOf(first, first)) }.hasVerifiedPurchase)
        assertEquals(100L, repository.readyAccount().credits.available)
        assertTrue(repository.readyAccount().conversionAccess.ads.adFree)
        assertFalse(repository.readyAccount().conversionAccess.ads.eligible)
        assertNotNull(repository.readyAccount().credits.nextExpiryAt)

        assertTrue(withTimeout(60_000) { verifier.process(listOf(first)) }.hasVerifiedPurchase)
        assertEquals(100L, repository.readyAccount().credits.available)

        val repeatToken = requiredArgument("fakePlayCredit100RepeatToken")
        assertTrue(
            withTimeout(60_000) {
                verifier.process(listOf(candidate("test_pack_100", repeatToken)))
            }.hasVerifiedPurchase,
        )
        assertEquals(200L, repository.readyAccount().credits.available)

        val secondPackToken = requiredArgument("fakePlayCredit500Token")
        assertTrue(
            withTimeout(60_000) {
                verifier.process(listOf(candidate("test_pack_500", secondPackToken)))
            }.hasVerifiedPurchase,
        )
        assertEquals(700L, repository.readyAccount().credits.available)

        val recoveryToken = requiredArgument("fakePlayCreditRecoveryToken")
        val recovered = withTimeout(60_000) {
            verifier.recover(listOf(candidate("test_pack_100", recoveryToken)))
        }
        assertTrue(recovered.hasVerifiedPurchase)
        assertEquals(800L, repository.readyAccount().credits.available)

        val retryToken = requiredArgument("fakePlayCreditConsumptionRetryToken")
        val noImmediateRetry = verifier(repository, store, catalog.mapTo(linkedSetOf()) { it.productId }, 0)
        val firstAttempt = withTimeout(60_000) {
            noImmediateRetry.process(listOf(candidate("test_pack_100", retryToken)))
        }
        assertTrue(firstAttempt.hasRetryableFailure)
        assertEquals(listOf(retryToken), store.all().map { it.purchaseToken })
        val afterRestart = verifier(repository, store, catalog.mapTo(linkedSetOf()) { it.productId })
        assertTrue(withTimeout(60_000) { afterRestart.retryPersisted() }.hasVerifiedPurchase)
        assertTrue(store.all().isEmpty())
        assertEquals(900L, repository.readyAccount().credits.available)
    }

    private fun verifier(
        repository: AccountRepository,
        store: PrivatePendingCreditPurchaseStore,
        products: Set<String>,
        retries: Int = 2,
    ) = CreditPurchaseVerifier(
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
        enabledProductIds = { products },
        maxAutomaticRetries = retries,
        retryDelay = {},
    )

    private suspend fun initializeRepository(): AccountRepository {
        FirebaseApp.getApps(InstrumentationRegistry.getInstrumentation().targetContext)
            .forEach { FirebaseAuth.getInstance(it).signOut() }
        val repository = BackendAccountStore.repository
        withTimeout(30_000) { repository.initialize() }
        return repository
    }

    private fun candidate(productId: String, token: String) =
        PlayPurchaseCandidate(productId, token, PlayPurchaseState.PURCHASED)

    private fun BackendResult<List<CreditProduct>>.requireCatalog(): List<CreditProduct> {
        if (this is BackendResult.Success) return value
        throw AssertionError("Expected successful credit catalog, got $this")
    }

    private fun AccountRepository.readyAccount(): AccountState =
        (state.value as AccountRepositoryState.Ready).account

    private fun requiredArgument(name: String): String =
        requireNotNull(InstrumentationRegistry.getArguments().getString(name)) {
            "Missing required fake fixture argument $name"
        }
}
