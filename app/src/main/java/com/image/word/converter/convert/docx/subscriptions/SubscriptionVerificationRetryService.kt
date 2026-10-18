package com.image.word.converter.convert.docx.subscriptions

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.image.word.converter.convert.docx.ads.AdsGate
import com.image.word.converter.convert.docx.backend.BackendAccountStore
import com.image.word.converter.convert.docx.backend.BackendClientStore
import com.image.word.converter.convert.docx.backend.BackendEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SubscriptionVerificationRetryService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val environment = BackendEnvironment.current()
        if (!environment.accountIntegrationEnabled || environment.isEmulator) return false
        runningJob = scope.launch {
            AdsGate.beginCriticalOperation()
            val shouldRetry = try {
                runCatching {
                    val verifier = BackendSubscriptionVerifier(
                        backendClient = BackendClientStore.client,
                        accountGateway = RepositoryAccountGateway(BackendAccountStore.repository),
                        pendingStore = PrivatePendingPurchaseStore(applicationContext),
                    )
                    val subscriptionRetry = verifier.retryPersisted().hasRetryableFailure
                    val catalog = when (val result = BackendClientStore.client.getCreditProductCatalog()) {
                        is com.image.word.converter.convert.docx.backend.BackendResult.Success -> result.value
                        is com.image.word.converter.convert.docx.backend.BackendResult.Failure -> emptyList()
                    }
                    val creditRetry = if (catalog.isEmpty()) {
                        PrivatePendingCreditPurchaseStore(applicationContext).all().isNotEmpty()
                    } else {
                        CreditPurchaseVerifier(
                            backendClient = BackendClientStore.client,
                            accountGateway = RepositoryAccountGateway(BackendAccountStore.repository),
                            pendingStore = PrivatePendingCreditPurchaseStore(applicationContext),
                            enabledProductIds = { catalog.mapTo(linkedSetOf()) { it.productId } },
                        ).retryPersisted().hasRetryableFailure
                    }
                    subscriptionRetry || creditRetry
                }.getOrDefault(true)
            } finally {
                AdsGate.endCriticalOperation()
            }
            jobFinished(params, shouldRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

object SubscriptionVerificationRetryScheduler {
    private const val JOB_ID = 0x53425632

    fun schedule(context: Context) {
        val environment = BackendEnvironment.current()
        if (!environment.accountIntegrationEnabled || environment.isEmulator) return
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, SubscriptionVerificationRetryService::class.java),
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(15 * 60 * 1_000L)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}
