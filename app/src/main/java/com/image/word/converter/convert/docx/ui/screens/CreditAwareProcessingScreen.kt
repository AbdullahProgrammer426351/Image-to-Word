package com.image.word.converter.convert.docx.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ads.InterstitialAdManager
import com.image.word.converter.convert.docx.backend.BackendAccountStore
import com.image.word.converter.convert.docx.backend.BackendClientStore
import com.image.word.converter.convert.docx.backend.BackendEnvironment
import com.image.word.converter.convert.docx.backend.BackendFailure
import com.image.word.converter.convert.docx.backend.BackendResult
import com.image.word.converter.convert.docx.backend.ConversionAccessPolicy
import com.image.word.converter.convert.docx.backend.ConversionBackendMode
import com.image.word.converter.convert.docx.backend.ImageConversionBatchRunner
import com.image.word.converter.convert.docx.backend.ImageConversionRequestFactory
import com.image.word.converter.convert.docx.backend.PreparedImageConversion
import com.image.word.converter.convert.docx.model.ConvertedItem
import com.image.word.converter.convert.docx.ui.components.LottieAssetView
import com.image.word.converter.convert.docx.ui.state.MainViewModel
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.util.DailyAttemptManager
import com.image.word.converter.convert.docx.util.decodeBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PreparedUiConversion(
    val bitmap: Bitmap,
    val prepared: PreparedImageConversion,
)

private data class ConversionDialogState(
    val title: String,
    val message: String,
    val retryItems: List<PreparedUiConversion>,
    val canContinueToResults: Boolean,
)

@Composable
fun CreditAwareProcessingScreen(
    activity: Activity,
    sessionState: SessionState,
    mainViewModel: MainViewModel,
    attemptManager: DailyAttemptManager,
    isSubscribed: Boolean,
    onResetToHome: () -> Unit,
    onDone: () -> Unit,
) {
    val mode = remember {
        ConversionAccessPolicy.backendMode(BackendEnvironment.current())
    }
    if (mode == ConversionBackendMode.LEGACY) {
        ProcessingScreen(
            activity = activity,
            sessionState = sessionState,
            mainViewModel = mainViewModel,
            attemptManager = attemptManager,
            isSubscribed = isSubscribed,
            onResetToHome = onResetToHome,
            onDone = onDone,
        )
    } else {
        V2ProcessingScreen(
            activity = activity,
            sessionState = sessionState,
            mainViewModel = mainViewModel,
            onResetToHome = onResetToHome,
            onDone = onDone,
        )
    }
}

@Composable
private fun V2ProcessingScreen(
    activity: Activity,
    sessionState: SessionState,
    mainViewModel: MainViewModel,
    onResetToHome: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by sessionState.uiState.collectAsState()
    val runner = remember { ImageConversionBatchRunner(BackendClientStore.client) }
    val requestFactory = remember { ImageConversionRequestFactory() }
    val convertedByIndex = remember { mutableStateMapOf<Int, ConvertedItem>() }
    val unresolvedByIndex = remember {
        mutableStateMapOf<Int, Pair<PreparedUiConversion, BackendResult.Failure>>()
    }
    var dialogState by remember { mutableStateOf<ConversionDialogState?>(null) }
    var isRunning by remember { mutableStateOf(true) }

    fun finishWithResults() {
        sessionState.setConvertedItems(convertedByIndex.toSortedMap().values.toList())
        InterstitialAdManager.shared.show(activity) { onDone() }
    }

    suspend fun runPrepared(items: List<PreparedUiConversion>) {
        if (items.isEmpty()) return
        isRunning = true
        dialogState = null
        val batch = runner.run(items.map { it.prepared })

        batch.items.forEach { item ->
            val uiItem = items.first { it.prepared.index == item.prepared.index }
            when (val result = item.result) {
                is BackendResult.Success -> {
                    unresolvedByIndex.remove(item.prepared.index)
                    if (!convertedByIndex.containsKey(item.prepared.index)) {
                        convertedByIndex[item.prepared.index] = mainViewModel.saveConvertedNow(
                            image = uiItem.bitmap,
                            fileName = uniqueV2FileName(mainViewModel, item.prepared.index),
                            fileUrl = result.value.resultUrl,
                        )
                    }
                }
                is BackendResult.Failure -> {
                    unresolvedByIndex[item.prepared.index] = uiItem to result
                }
            }
        }

        BackendAccountStore.repository.refresh()
        convertedByIndex.values.forEach(mainViewModel::downloadAndUpdateItem)
        sessionState.setConvertedItems(convertedByIndex.toSortedMap().values.toList())
        isRunning = false

        if (unresolvedByIndex.isEmpty()) {
            finishWithResults()
            return
        }
        val retryItems = unresolvedByIndex.values
            .filter { (_, failure) -> failure.isSafelyRetryable() }
            .map { it.first }
        dialogState = buildConversionDialog(
            context = context,
            failures = unresolvedByIndex.values.map { it.second },
            retryItems = retryItems,
            successCount = convertedByIndex.size,
        )
    }

    LaunchedEffect(Unit) {
        val imageUris = session.selectedImageUris
        if (imageUris.isEmpty()) {
            onResetToHome()
            return@LaunchedEffect
        }
        val images = withContext(Dispatchers.IO) {
            imageUris.mapNotNull { decodeBitmap(context.contentResolver, it) }
        }
        if (images.size != imageUris.size) {
            isRunning = false
            dialogState = ConversionDialogState(
                title = context.getString(R.string.invalid_image_title),
                message = context.getString(R.string.invalid_image_message),
                retryItems = emptyList(),
                canContinueToResults = false,
            )
            return@LaunchedEffect
        }
        val encodedImages = withContext(Dispatchers.Default) {
            images.map(::encodeCanonicalJpeg)
        }
        val prepared = requestFactory.prepare(encodedImages)
        runPrepared(
            images.zip(prepared) { bitmap, request -> PreparedUiConversion(bitmap, request) },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(20.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(18.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isRunning) {
                LottieAssetView(fileName = "scan_gif.json", modifier = Modifier.size(200.dp))
            }
            Text(
                text = stringResource(
                    if (isRunning) R.string.converting_images else R.string.conversion_needs_attention,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.word_processing_message),
                modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }

    dialogState?.let { dialog ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                if (dialog.retryItems.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { runPrepared(dialog.retryItems) } }) {
                        Text(stringResource(R.string.retry))
                    }
                } else {
                    TextButton(
                        onClick = {
                            if (dialog.canContinueToResults) finishWithResults() else onResetToHome()
                        },
                    ) {
                        Text(
                            stringResource(
                                if (dialog.canContinueToResults) R.string.continue_label else R.string.ok,
                            ),
                        )
                    }
                }
            },
            dismissButton = if (dialog.retryItems.isNotEmpty()) {
                {
                    TextButton(
                        onClick = {
                            if (dialog.canContinueToResults) finishWithResults() else onResetToHome()
                        },
                    ) {
                        Text(
                            stringResource(
                                if (dialog.canContinueToResults) R.string.continue_label else R.string.cancel,
                            ),
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}

private fun encodeCanonicalJpeg(bitmap: Bitmap): String {
    val bytes = ByteArrayOutputStream().use { stream ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)) { "Unable to encode JPEG" }
        stream.toByteArray()
    }
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private fun uniqueV2FileName(mainViewModel: MainViewModel, index: Int): String {
    val timestamp = System.currentTimeMillis()
    var fileName = "Word_${timestamp}_$index"
    var attempt = 1
    while (mainViewModel.doesNameExist(fileName)) {
        fileName = "Word_${timestamp}_${index}_${attempt++}"
    }
    return fileName
}

private fun BackendResult.Failure.isSafelyRetryable(): Boolean = when (val value = failure) {
    is BackendFailure.AppCheck,
    is BackendFailure.Network -> true
    is BackendFailure.Server -> value.error.code == "CONVERSION_PENDING"
    else -> false
}

private fun buildConversionDialog(
    context: Context,
    failures: List<BackendResult.Failure>,
    retryItems: List<PreparedUiConversion>,
    successCount: Int,
): ConversionDialogState {
    val serverErrors = failures.mapNotNull { it.failure as? BackendFailure.Server }
    val primary = serverErrors.firstOrNull()
    val hasAuthenticationFailure = failures.any {
        it.failure is BackendFailure.Authentication || it.failure is BackendFailure.AppCheck
    }
    val (title, detail) = when {
        hasAuthenticationFailure -> context.getString(R.string.authentication_failed_title) to
            context.getString(R.string.authentication_failed_message)
        primary?.error?.code == "INSUFFICIENT_CREDITS" -> context.getString(R.string.insufficient_credits_title) to
            context.getString(
                R.string.insufficient_credits_message,
                primary.error.availableCredits ?: 0,
                primary.error.requiredCredits ?: 0,
            )
        primary?.error?.code == "UNAUTHENTICATED" -> context.getString(R.string.authentication_failed_title) to
            context.getString(R.string.authentication_failed_message)
        primary?.error?.code in setOf("INVALID_BASE64", "INVALID_REQUEST") -> context.getString(R.string.invalid_image_title) to
            context.getString(R.string.invalid_image_message)
        primary?.error?.code == "FILE_TOO_LARGE" -> context.getString(R.string.file_too_large_title) to
            context.getString(R.string.file_too_large_message)
        primary?.error?.code == "CONVERSION_DISABLED" -> context.getString(R.string.conversion_unavailable_title) to
            context.getString(R.string.conversion_unavailable_message)
        primary?.error?.code == "CONVERSION_FAILED" -> context.getString(R.string.conversion_failed_title) to
            context.getString(R.string.conversion_failed_message)
        primary?.error?.code == "CONVERSION_PENDING" -> context.getString(R.string.conversion_pending_title) to
            context.getString(R.string.conversion_pending_message)
        else -> context.getString(R.string.conversion_failed_title) to
            if (failures.any { it.failure is BackendFailure.Network }) {
                context.getString(R.string.conversion_network_unknown_message)
            } else {
                context.getString(R.string.conversion_failed_message)
            }
    }
    return ConversionDialogState(
        title = title,
        message = if (successCount > 0) {
            context.getString(R.string.partial_conversion_message, successCount, failures.size, detail)
        } else {
            detail
        },
        retryItems = retryItems,
        canContinueToResults = successCount > 0,
    )
}
