package com.image.word.converter.convert.docx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.components.PremiumBanner
import com.image.word.converter.convert.docx.ui.components.SectionTitle
import com.image.word.converter.convert.docx.util.AppLinks

@Composable
fun SettingsTabScreen(
    onOpenSubscription: () -> Unit,
    isSubscribed: Boolean,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PremiumBanner(visible = !isSubscribed, onClick = onOpenSubscription)

        SectionTitle(title = stringResource(R.string.other))
        SettingsGroup {
            SettingsItem(
                icon = "ic_rate_us_s.svg",
                title = stringResource(R.string.rate_us),
                subtitle = stringResource(R.string.love_our_app_give_us_5_stars),
                onClick = { AppLinks.rateApp(context) },
            )
            SettingsItem(
                icon = "ic_share_us_s.svg",
                title = stringResource(R.string.share_the_app),
                subtitle = stringResource(R.string.invite_friends_to_try_the_app),
                onClick = { AppLinks.shareApp(context) },
            )
            SettingsItem(
                icon = "ic_feed_back_s.svg",
                title = stringResource(R.string.help_us_improve),
                subtitle = stringResource(R.string.we_value_your_suggestions_and_ideas),
                onClick = { AppLinks.sendFeedback(context) },
            )
            SettingsItem(
                icon = "ic_more_app_s.svg",
                title = stringResource(R.string.more_from_us),
                subtitle = stringResource(R.string.discover_other_apps_by_us),
                onClick = { AppLinks.openDeveloperPage(context) },
            )
            SettingsItem(
                icon = "ic_privacy_s.svg",
                title = stringResource(R.string.privacy_policy),
                subtitle = stringResource(R.string.read_our_privacy_policy),
                onClick = { AppLinks.openPrivacy(context) },
            )
            SettingsItem(
                icon = "ic_disclaimer_s.svg",
                title = stringResource(R.string.important_info),
                subtitle = stringResource(R.string.check_essential_details_for_safe_app_use),
                showDivider = false,
                onClick = { AppLinks.openDisclaimer(context) },
            )
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            ),
        content = content,
    )
}

@Composable
private fun SettingsItem(
    icon: String,
    title: String,
    subtitle: String,
    showDivider:Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssetImage(name = icon, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        if (showDivider){ HorizontalDivider(modifier = Modifier.padding(top = 12.dp)) }
    }
}
