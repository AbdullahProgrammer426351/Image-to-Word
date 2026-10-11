package com.image.word.converter.convert.docx.backend

import com.image.word.converter.convert.docx.BuildConfig

data class BackendEnvironment(
    val name: String,
    val accountIntegrationEnabled: Boolean,
    val firebaseProjectId: String,
    val functionsBaseUrl: String,
    val authEmulatorHost: String?,
    val authEmulatorPort: Int?,
    val isolateProductionServices: Boolean,
) {
    val isEmulator: Boolean = name == EMULATOR

    init {
        if (isEmulator) {
            require(accountIntegrationEnabled) { "The emulator environment must enable account integration" }
            require(firebaseProjectId == DEMO_PROJECT_ID) { "Debug integration must use the demo Firebase project" }
            require(functionsBaseUrl == DEMO_FUNCTIONS_BASE_URL) { "Debug integration must use the Functions Emulator" }
            require(authEmulatorHost == DEMO_AUTH_HOST && authEmulatorPort == DEMO_AUTH_PORT) {
                "Debug integration must use the Auth Emulator"
            }
            require(isolateProductionServices) { "Emulator integration must isolate production services" }
        } else if (accountIntegrationEnabled) {
            require(functionsBaseUrl.startsWith("https://")) {
                "An enabled non-emulator backend must use HTTPS"
            }
        }
    }

    fun endpoint(functionName: BackendFunction): String {
        check(accountIntegrationEnabled) { "Backend account integration is disabled for this build" }
        return functionsBaseUrl + functionName.path
    }

    companion object {
        const val EMULATOR = "emulator"
        const val DEMO_PROJECT_ID = "demo-converter-credit"
        const val DEMO_FUNCTIONS_BASE_URL =
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/"
        const val DEMO_AUTH_HOST = "10.0.2.2"
        const val DEMO_AUTH_PORT = 9099

        fun current(): BackendEnvironment = BackendEnvironment(
            name = BuildConfig.BACKEND_ENVIRONMENT,
            accountIntegrationEnabled = BuildConfig.BACKEND_ACCOUNT_INTEGRATION_ENABLED,
            firebaseProjectId = BuildConfig.BACKEND_FIREBASE_PROJECT_ID,
            functionsBaseUrl = BuildConfig.BACKEND_FUNCTIONS_BASE_URL,
            authEmulatorHost = BuildConfig.BACKEND_AUTH_EMULATOR_HOST.ifBlank { null },
            authEmulatorPort = BuildConfig.BACKEND_AUTH_EMULATOR_PORT.takeIf { it > 0 },
            isolateProductionServices = BuildConfig.ISOLATE_PRODUCTION_SERVICES,
        )
    }
}

enum class BackendFunction(val path: String) {
    BOOTSTRAP_USER("bootstrapUserV2"),
    GET_ACCOUNT_STATE("getAccountStateV2"),
    IMAGE_TO_WORD("imageToWordV2"),
    VERIFY_SUBSCRIPTION_PURCHASE("verifySubscriptionPurchaseV2"),
}
