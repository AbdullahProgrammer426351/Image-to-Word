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
import com.image.word.converter.convert.docx.backend.ConversionAccessMode

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
    onOpenSubscription: () -> Unit,
) {
    val account = when (accountState) {
        is AccountRepositoryState.Ready -> accountState.account
        is AccountRepositoryState.Loading -> accountState.previous
        is AccountRepositoryState.Degraded -> accountState.previous
        AccountRepositoryState.Disabled -> null
    }
    val label = when {
        account == null && accountState is AccountRepositoryState.Loading ->
            stringResource(R.string.credits_loading)
        account == null -> stringResource(R.string.credits_unavailable)
        else -> when (account.conversionAccess.mode) {
            ConversionAccessMode.FREE -> if (account.conversionAccess.freeConversion.available) {
                stringResource(
                    R.string.free_conversions_remaining_count,
                    account.conversionAccess.freeConversion.remaining,
                )
            } else {
                stringResource(R.string.no_free_conversions_remaining)
            }
            ConversionAccessMode.TEMPORARY_UNLIMITED ->
                stringResource(R.string.temporary_unlimited_conversion_active)
            ConversionAccessMode.CREDIT_BASED ->
                stringResource(R.string.credits_available, account.credits.available)
            ConversionAccessMode.NONE -> stringResource(R.string.no_conversion_entitlement_subscribe)
        }
    }

    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable(
                enabled = account?.conversionAccess?.mode == ConversionAccessMode.NONE,
                onClick = onOpenSubscription,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}
