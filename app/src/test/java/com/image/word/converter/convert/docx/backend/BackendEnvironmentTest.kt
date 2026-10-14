package com.image.word.converter.convert.docx.backend

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendEnvironmentTest {
    @Test
    fun emulatorRequiresDemoProjectAppAndEndpoint() {
        val environment = emulator()

        assertTrue(environment.isEmulator)
        assertTrue(environment.endpoint(BackendFunction.IMAGE_TO_WORD).contains("imageToWordV2"))
        assertThrows(IllegalArgumentException::class.java) {
            emulator(firebaseProjectId = BackendEnvironment.PRODUCTION_PROJECT_ID)
        }
    }

    @Test
    fun productionV2RejectsUnexpectedProjectAppOrEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {
            production(firebaseProjectId = "attacker-project")
        }
        assertThrows(IllegalArgumentException::class.java) {
            production(firebaseAppId = "1:bad:android:bad")
        }
        assertThrows(IllegalArgumentException::class.java) {
            production(functionsBaseUrl = "http://us-central1-converter-api-project.cloudfunctions.net/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            production(name = "unexpected-enabled-environment")
        }
    }

    @Test
    fun productionV2AcceptsOnlyApprovedIdentityAndEndpoint() {
        val environment = production()

        assertTrue(environment.accountIntegrationEnabled)
        assertTrue(environment.endpoint(BackendFunction.BOOTSTRAP_USER).startsWith("https://"))
    }

    @Test
    fun disabledReleaseCannotCarryBackendConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            disabled(functionsBaseUrl = BackendEnvironment.PRODUCTION_FUNCTIONS_BASE_URL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            disabled(name = "unknown-disabled-environment")
        }
    }

    private fun emulator(
        firebaseProjectId: String = BackendEnvironment.DEMO_PROJECT_ID,
        firebaseAppId: String = BackendEnvironment.DEMO_FIREBASE_APP_ID,
    ) = BackendEnvironment(
        name = BackendEnvironment.EMULATOR,
        accountIntegrationEnabled = true,
        firebaseProjectId = firebaseProjectId,
        functionsBaseUrl = BackendEnvironment.DEVICE_DEMO_FUNCTIONS_BASE_URL,
        authEmulatorHost = BackendEnvironment.DEVICE_DEMO_AUTH_HOST,
        authEmulatorPort = BackendEnvironment.DEMO_AUTH_PORT,
        isolateProductionServices = true,
        firebaseAppId = firebaseAppId,
    )

    private fun production(
        name: String = BackendEnvironment.PRODUCTION_INTERNAL,
        firebaseProjectId: String = BackendEnvironment.PRODUCTION_PROJECT_ID,
        firebaseAppId: String = BackendEnvironment.PRODUCTION_FIREBASE_APP_ID,
        functionsBaseUrl: String = BackendEnvironment.PRODUCTION_FUNCTIONS_BASE_URL,
    ) = BackendEnvironment(
        name = name,
        accountIntegrationEnabled = true,
        firebaseProjectId = firebaseProjectId,
        functionsBaseUrl = functionsBaseUrl,
        authEmulatorHost = null,
        authEmulatorPort = null,
        isolateProductionServices = false,
        firebaseAppId = firebaseAppId,
    )

    private fun disabled(
        name: String = BackendEnvironment.PRODUCTION_DISABLED,
        functionsBaseUrl: String = "",
    ) = BackendEnvironment(
        name = name,
        accountIntegrationEnabled = false,
        firebaseProjectId = BackendEnvironment.PRODUCTION_PROJECT_ID,
        functionsBaseUrl = functionsBaseUrl,
        authEmulatorHost = null,
        authEmulatorPort = null,
        isolateProductionServices = false,
        firebaseAppId = BackendEnvironment.PRODUCTION_FIREBASE_APP_ID,
    )
}
