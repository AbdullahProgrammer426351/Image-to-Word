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
    val firebaseAppId: String = "",
) {
    val isEmulator: Boolean = name == EMULATOR

    init {
        if (isEmulator) {
            require(accountIntegrationEnabled) { "The emulator environment must enable account integration" }
            require(firebaseProjectId == DEMO_PROJECT_ID) { "Debug integration must use the demo Firebase project" }
            require(functionsBaseUrl in DEMO_FUNCTIONS_BASE_URLS) { "Debug integration must use the Functions Emulator" }
            require(authEmulatorHost in DEMO_AUTH_HOSTS && authEmulatorPort == DEMO_AUTH_PORT) {
                "Debug integration must use the Auth Emulator"
            }
            require(isolateProductionServices) { "Emulator integration must isolate production services" }
            require(firebaseAppId == DEMO_FIREBASE_APP_ID) {
                "Debug integration must use the demo Firebase Android app"
            }
        } else if (accountIntegrationEnabled) {
            require(name == PRODUCTION_INTERNAL) {
                "Only the approved internal environment may enable V2 production access"
            }
            require(firebaseProjectId == PRODUCTION_PROJECT_ID) {
                "V2 production access must use the approved Firebase project"
            }
            require(firebaseAppId == PRODUCTION_FIREBASE_APP_ID) {
                "V2 production access must use the approved Firebase Android app"
            }
            require(functionsBaseUrl == PRODUCTION_FUNCTIONS_BASE_URL) {
                "V2 production access must use the approved Functions endpoint"
            }
            require(functionsBaseUrl.startsWith("https://") && !functionsBaseUrl.contains("?")) {
                "An enabled non-emulator backend must use a secure, exact HTTPS endpoint"
            }
            require(authEmulatorHost == null && authEmulatorPort == null) {
                "Production V2 must not configure an Auth emulator"
            }
            require(!isolateProductionServices) {
                "Production V2 must not isolate production services"
            }
        } else {
            require(name == PRODUCTION_DISABLED) {
                "Unknown disabled backend environment"
            }
            require(functionsBaseUrl.isBlank()) { "Disabled release must not configure a backend URL" }
            require(authEmulatorHost == null && authEmulatorPort == null) {
                "Disabled release must not configure an Auth emulator"
            }
            require(!isolateProductionServices) { "Disabled release must not isolate production services" }
        }
    }

    fun endpoint(functionName: BackendFunction): String {
        check(accountIntegrationEnabled) { "Backend account integration is disabled for this build" }
        return functionsBaseUrl + functionName.path
    }

    companion object {
        const val EMULATOR = "emulator"
        const val PRODUCTION_DISABLED = "production-disabled"
        const val PRODUCTION_INTERNAL = "production-internal"
        const val DEMO_PROJECT_ID = "demo-converter-credit"
        const val PRODUCTION_PROJECT_ID = "converter-api-project"
        const val DEMO_FIREBASE_APP_ID = "1:000000000000:android:demo-converter-credit"
        const val PRODUCTION_FIREBASE_APP_ID = "1:1080587695450:android:3dbc635eb260638e852f63"
        const val DEMO_FUNCTIONS_BASE_URL =
            "http://10.0.2.2:5001/demo-converter-credit/us-central1/"
        const val DEVICE_DEMO_FUNCTIONS_BASE_URL =
            "http://127.0.0.1:5001/demo-converter-credit/us-central1/"
        const val DEMO_AUTH_HOST = "10.0.2.2"
        const val DEVICE_DEMO_AUTH_HOST = "127.0.0.1"
        const val DEMO_AUTH_PORT = 9099
        const val PRODUCTION_FUNCTIONS_BASE_URL =
            "https://us-central1-converter-api-project.cloudfunctions.net/"
        private val DEMO_FUNCTIONS_BASE_URLS =
            setOf(DEMO_FUNCTIONS_BASE_URL, DEVICE_DEMO_FUNCTIONS_BASE_URL)
        private val DEMO_AUTH_HOSTS = setOf(DEMO_AUTH_HOST, DEVICE_DEMO_AUTH_HOST)

        fun current(): BackendEnvironment = BackendEnvironment(
            name = BuildConfig.BACKEND_ENVIRONMENT,
            accountIntegrationEnabled = BuildConfig.BACKEND_ACCOUNT_INTEGRATION_ENABLED,
            firebaseProjectId = BuildConfig.BACKEND_FIREBASE_PROJECT_ID,
            functionsBaseUrl = BuildConfig.BACKEND_FUNCTIONS_BASE_URL,
            authEmulatorHost = BuildConfig.BACKEND_AUTH_EMULATOR_HOST.ifBlank { null },
            authEmulatorPort = BuildConfig.BACKEND_AUTH_EMULATOR_PORT.takeIf { it > 0 },
            isolateProductionServices = BuildConfig.ISOLATE_PRODUCTION_SERVICES,
            firebaseAppId = BuildConfig.BACKEND_FIREBASE_APP_ID,
        )
    }
}

enum class BackendFunction(val path: String) {
    BOOTSTRAP_USER("bootstrapUserV2"),
    GET_ACCOUNT_STATE("getAccountStateV2"),
    IMAGE_TO_WORD("imageToWordV2"),
    VERIFY_SUBSCRIPTION_PURCHASE("verifySubscriptionPurchaseV2"),
    GET_CREDIT_PRODUCT_CATALOG("getCreditProductCatalogV2"),
    VERIFY_CREDIT_PURCHASE("verifyCreditPurchaseV2"),
    RECOVER_CREDIT_PURCHASE("recoverCreditPurchaseV2"),
}
