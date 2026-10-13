package com.image.word.converter.convert.docx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.util.DailyAttemptManager
import com.image.word.converter.convert.docx.backend.AccountRepositoryState

@Composable
fun RemainingAttemptBanner(
    attemptManager: DailyAttemptManager,
    isSubscribed: Boolean,
    onPremium: () -> Unit,
) {
    if (isSubscribed) return
    val remaining = attemptManager.remainingAttempts
    val text = buildAnnotatedString {
        if (remaining <= 0) {
            append(stringResource(R.string.no_free_uses_left))
            append(" ")
            withStyle(SpanStyle(color = Color(0xFFFF9D00), fontWeight = FontWeight.Bold)) {
                append(stringResource(R.string.go_premium))
            }
            append(" ")
            append(stringResource(R.string.for_unlimited_access))
        } else {
            append(stringResource(R.string.free_uses_remaining, remaining))
            append(" ")
            withStyle(SpanStyle(color = Color(0xFFFF9D00), fontWeight = FontWeight.Bold)) {
                append(stringResource(R.string.go_premium))
            }
            append(" ")
            append(stringResource(R.string.for_unlimited_access))
        }
    }

    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable(onClick = onPremium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun BackendCreditsBanner(
    accountState: AccountRepositoryState,
) {
    val credits = when (accountState) {
        is AccountRepositoryState.Ready -> accountState.account.credits.available
        is AccountRepositoryState.Loading -> accountState.previous?.credits?.available
        is AccountRepositoryState.Degraded -> accountState.previous?.credits?.available
        AccountRepositoryState.Disabled -> null
    }
    val label = when {
        credits != null -> stringResource(R.string.credits_available, credits)
        accountState is AccountRepositoryState.Loading -> stringResource(R.string.credits_loading)
        else -> stringResource(R.string.credits_unavailable)
    }

    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}
