package com.image.word.converter.convert.docx.subscriptions

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.theme.LightSurfaceVariant
import com.image.word.converter.convert.docx.ui.theme.WordGradients
import com.image.word.converter.convert.docx.ui.theme.WordGrayTextSub
import com.image.word.converter.convert.docx.ui.theme.wordPlanBorderUnselected

@Composable
fun SubscriptionScreen(
    onClose: () -> Unit,
    viewModel: SubscriptionViewModel = viewModel(),
) {
    BackHandler(onBack = onClose)

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var showAlert by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isSubscribed) {
        if (uiState.isSubscribed) onClose()
    }
    LaunchedEffect(uiState.restoreToastMessage) {
        uiState.restoreToastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeRestoreToast()
        }
    }
    LaunchedEffect(uiState.alertMessage) {
        uiState.alertMessage?.takeIf { it.isNotBlank() }?.let {
            alertMessage = it
            showAlert = true
        }
    }

    val premiumItems = listOf(
        R.string.sub_feature_unlimited_scanning,
        R.string.sub_feature_high_accuracy,
        R.string.sub_feature_fast_smooth,
        R.string.sub_feature_ads_free,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.ic_sub_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(234.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                        start = 15.dp,
                        end = 15.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.Black)
                }
                Text(
                    text = stringResource(R.string.restore),
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(15.dp))
                        .clickable { viewModel.restorePurchases() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SubscriptionHeader()

            premiumItems.forEach { resId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssetImage(name = "ic_bullet.svg", modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(resId),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                uiState.products.forEach { product ->
                    val selected = uiState.selectedProductId == product.id
                    val borderBrush = if (selected) {
                        Brush.horizontalGradient(WordGradients.subscribeButton)
                    } else {
                        val unselectedColor = wordPlanBorderUnselected()
                        Brush.horizontalGradient(listOf(unselectedColor, unselectedColor))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 5.dp)
                            .border(4.dp, borderBrush, RoundedCornerShape(16.dp))
                            .background(
                                if (selected) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { viewModel.selectProduct(product.id) }
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${product.priceFormatted} / ${billingUnitLabel(product.billingPeriod)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = product.weeklyEquivalentText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(WordGradients.subscribeButton))
                        .clickable(enabled = activity != null) {
                            activity?.let { viewModel.purchase(it) }
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.subscribe),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = stringResource(R.string.auto_renewal_cancel_anytime),
                    style = MaterialTheme.typography.bodySmall,
                    color = WordGrayTextSub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.subscription_legal_text_ios),
                    style = MaterialTheme.typography.bodySmall,
                    color = WordGrayTextSub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GradientLink(stringResource(R.string.privacy_policy)) {
                        openUrl(context, "https://health-fitness-pro.fit/privacy")
                    }
                    Text(" | ", color = MaterialTheme.colorScheme.onBackground)
                    GradientLink(stringResource(R.string.terms_of_use)) {
                        openUrl(context, "https://health-fitness-pro.fit/terms")
                    }
                }
            }
        }
    }

    if (uiState.isPurchasing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.processing_purchase),
                    modifier = Modifier.padding(top = 10.dp),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.please_wait_purchase),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(stringResource(R.string.subscription)) },
            text = { Text(alertMessage) },
            confirmButton = {
                TextButton(onClick = { showAlert = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun SubscriptionHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AssetImage(name = "ic_crown_sub_setting.png", modifier = Modifier.size(50.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.upgrade_to))
                    append(" ")
                }
                withStyle(
                    SpanStyle(
                        brush = Brush.horizontalGradient(WordGradients.premiumText),
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(stringResource(R.string.premium_label))
                }
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp),
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.subscription_desc_ios),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GradientLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            brush = Brush.horizontalGradient(WordGradients.premiumText),
            fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun billingUnitLabel(period: String): String {
    return when (period) {
        "P1W" -> stringResource(R.string.week)
        "P1M" -> stringResource(R.string.month)
        "P1Y" -> stringResource(R.string.year)
        else -> stringResource(R.string.month)
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
