package com.espressotech.monetization.subscriptions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun rememberSubscriptionCheckoutLauncher(
    viewModel: SubscriptionViewModel,
): (SubscriptionCheckoutResolution) -> Unit {
    val pendingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.handlePurchaseResult(result.resultCode, result.data) }
    val directLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> viewModel.handlePurchaseResult(result.resultCode, result.data) }

    return { resolution ->
        when (resolution) {
            is SubscriptionCheckoutResolution.Pending -> pendingLauncher.launch(
                IntentSenderRequest.Builder(resolution.value).build(),
            )
            is SubscriptionCheckoutResolution.Direct -> directLauncher.launch(resolution.value)
        }
    }
}
