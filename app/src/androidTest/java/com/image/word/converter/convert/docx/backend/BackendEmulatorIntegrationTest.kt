package com.image.word.converter.convert.docx.backend

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.image.word.converter.convert.docx.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendEmulatorIntegrationTest {
    @Test
    fun anonymousAuth_bootstrap_returnsAuthoritativeAccountState() = runBlocking {
        assertTrue(BuildConfig.DEBUG)
        val environment = BackendEnvironment.current()
        assertTrue(environment.isEmulator)
        assertEquals(BackendEnvironment.DEMO_PROJECT_ID, environment.firebaseProjectId)

        val repository = BackendAccountStore.repository
        withTimeout(30_000) {
            repository.initialize()
        }

        val ready = repository.state.value as? AccountRepositoryState.Ready
        assertTrue(
            "Expected Auth Emulator -> bootstrapUserV2 -> AccountState, got ${repository.state.value}",
            ready != null,
        )
        assertTrue(ready!!.account.user.id.isNotBlank())
        assertTrue(ready.account.credits.available >= 0)
    }
}
