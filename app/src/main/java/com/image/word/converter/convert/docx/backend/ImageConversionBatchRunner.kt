package com.image.word.converter.convert.docx.backend

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

data class PreparedImageConversion(
    val index: Int,
    val request: ImageConversionRequest,
)

data class ImageConversionItemResult(
    val prepared: PreparedImageConversion,
    val result: BackendResult<ImageConversionResponse>,
    val requestAttempts: Int,
)

data class ImageConversionBatchResult(
    val items: List<ImageConversionItemResult>,
) {
    val successes: List<ImageConversionItemResult>
        get() = items.filter { it.result is BackendResult.Success }

    val failures: List<ImageConversionItemResult>
        get() = items.filter { it.result is BackendResult.Failure }
}

class ImageConversionRequestFactory(
    private val keyGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun prepare(encodedImages: List<String>): List<PreparedImageConversion> =
        encodedImages.mapIndexed { index, image ->
            require(image.isNotBlank()) { "Encoded image must not be blank" }
            PreparedImageConversion(
                index = index,
                request = ImageConversionRequest(
                    image = image,
                    idempotencyKey = keyGenerator(),
                ),
            )
        }
}

class ImageConversionBatchRunner(
    private val backendClient: BackendClient,
    private val maxAutomaticRetries: Int = 2,
    private val retryDelay: suspend (retryNumber: Int) -> Unit = { retryNumber ->
        delay(1_000L shl (retryNumber - 1).coerceAtMost(2))
    },
) {
    suspend fun run(
        prepared: List<PreparedImageConversion>,
    ): ImageConversionBatchResult = coroutineScope {
        val dispatcher = Dispatchers.IO.limitedParallelism(MAX_PARALLEL_CONVERSIONS)
        val items = prepared.map { item ->
            async(dispatcher) { executeOne(item) }
        }.awaitAll()
        ImageConversionBatchResult(items)
    }

    private suspend fun executeOne(
        prepared: PreparedImageConversion,
    ): ImageConversionItemResult {
        var attempts = 0
        while (true) {
            attempts += 1
            val result = backendClient.imageToWord(prepared.request)
            if (!result.shouldRetry() || attempts > maxAutomaticRetries) {
                return ImageConversionItemResult(prepared, result, attempts)
            }
            retryDelay(attempts)
        }
    }

    private fun BackendResult<ImageConversionResponse>.shouldRetry(): Boolean {
        val failure = (this as? BackendResult.Failure)?.failure ?: return false
        return when (failure) {
            is BackendFailure.Network -> true
            is BackendFailure.Server -> failure.error.code == "CONVERSION_PENDING"
            else -> false
        }
    }

    private companion object {
        const val MAX_PARALLEL_CONVERSIONS = 2
    }
}

enum class ConversionBackendMode {
    V2,
    LEGACY,
}

object ConversionAccessPolicy {
    fun backendMode(environment: BackendEnvironment): ConversionBackendMode =
        if (environment.accountIntegrationEnabled) ConversionBackendMode.V2 else ConversionBackendMode.LEGACY

    fun canStart(
        mode: ConversionBackendMode,
        isLocallySubscribed: Boolean,
        remainingLocalAttempts: Int,
    ): Boolean = when (mode) {
        ConversionBackendMode.V2 -> true
        ConversionBackendMode.LEGACY -> isLocallySubscribed || remainingLocalAttempts > 0
    }
}
