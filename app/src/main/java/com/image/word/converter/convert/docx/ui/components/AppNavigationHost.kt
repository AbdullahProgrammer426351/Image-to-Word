package com.image.word.converter.convert.docx.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppNavigationHost(
    showLimitDialog: Boolean,
    onDismissLimit: () -> Unit,
    onSubscribeFromLimit: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (showLimitDialog) {
            LimitDialog(
                onDismiss = onDismissLimit,
                onSubscribe = onSubscribeFromLimit,
            )
        }
    }
}
