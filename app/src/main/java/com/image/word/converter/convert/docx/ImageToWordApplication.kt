package com.image.word.converter.convert.docx

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.image.word.converter.convert.docx.ads.AdsGate
import com.image.word.converter.convert.docx.backend.AccountRepository
import com.image.word.converter.convert.docx.backend.AccountRepositoryState
import com.image.word.converter.convert.docx.backend.BackendAccountStore
import com.image.word.converter.convert.docx.backend.BackendEnvironment
import com.image.word.converter.convert.docx.backend.DisabledBackendClient
import com.image.word.converter.convert.docx.backend.FirebaseAnonymousAuthSession
import com.image.word.converter.convert.docx.backend.HttpBackendClient
import com.image.word.converter.convert.docx.backend.UrlConnectionBackendTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ImageToWordApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val environment = BackendEnvironment.current()
        isolateProductionServicesIfNeeded(environment)

        val firebaseAuth = createBackendFirebaseAuth(environment)
        val authSession = FirebaseAnonymousAuthSession(firebaseAuth)
        val backendClient = if (environment.accountIntegrationEnabled) {
            HttpBackendClient(
                environment = environment,
                authSession = authSession,
                transport = UrlConnectionBackendTransport(),
            )
        } else {
            DisabledBackendClient()
        }
        val repository = AccountRepository(
            integrationEnabled = environment.accountIntegrationEnabled,
            authSession = authSession,
            backendClient = backendClient,
        )
        BackendAccountStore.repository = repository

        if (environment.accountIntegrationEnabled) {
            applicationScope.launch { logDiagnosticState(environment, repository) }
            applicationScope.launch { repository.initialize() }
        }
    }

    private fun createBackendFirebaseAuth(environment: BackendEnvironment): FirebaseAuth {
        if (!environment.isEmulator) return FirebaseAuth.getInstance()

        val host = checkNotNull(environment.authEmulatorHost)
        val port = checkNotNull(environment.authEmulatorPort)

        // Also fail-safe the legacy default Auth singleton so a debug interaction cannot
        // silently authenticate against production. Its conversion endpoint is otherwise untouched.
        FirebaseAuth.getInstance().useEmulator(host, port)

        val localApp = FirebaseApp.getApps(this).firstOrNull { it.name == BACKEND_FIREBASE_APP_NAME }
            ?: FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApplicationId(LOCAL_FIREBASE_APPLICATION_ID)
                    .setApiKey(LOCAL_FIREBASE_API_KEY)
                    .setProjectId(environment.firebaseProjectId)
                    .build(),
                BACKEND_FIREBASE_APP_NAME,
            )
        return FirebaseAuth.getInstance(localApp).also { it.useEmulator(host, port) }
    }

    private fun isolateProductionServicesIfNeeded(environment: BackendEnvironment) {
        if (!environment.isolateProductionServices) return
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        AdsGate.canRequestAds = false
        AdsGate.canShowResumeAppOpen = false
    }

    private suspend fun logDiagnosticState(
        environment: BackendEnvironment,
        repository: AccountRepository,
    ) {
        Log.d(LOG_TAG, "backendEnvironment=${environment.name}, project=${environment.firebaseProjectId}")
        repository.state.collectLatest { state ->
            when (state) {
                AccountRepositoryState.Disabled -> Log.d(LOG_TAG, "accountIntegration=disabled")
                is AccountRepositoryState.Loading -> Log.d(LOG_TAG, "accountState=loading")
                is AccountRepositoryState.Ready -> Log.d(
                    LOG_TAG,
                    "authenticated=true, backendUserResolved=true, " +
                        "availableCredits=${state.account.credits.available}, " +
                        "subscriptionStatus=${state.account.subscription.status}",
                )
                is AccountRepositoryState.Degraded -> Log.w(
                    LOG_TAG,
                    "accountState=degraded, failure=${state.failure::class.simpleName}",
                )
            }
        }
    }

    private companion object {
        const val LOG_TAG = "BACKEND_ACCOUNT"
        const val BACKEND_FIREBASE_APP_NAME = "backend-emulator"
        const val LOCAL_FIREBASE_APPLICATION_ID = "1:000000000000:android:demo-converter-credit"
        const val LOCAL_FIREBASE_API_KEY = "demo-converter-credit"
    }
}
