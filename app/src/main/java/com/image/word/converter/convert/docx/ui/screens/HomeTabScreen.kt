package com.image.word.converter.convert.docx.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.components.MiniOptionCard
import com.image.word.converter.convert.docx.ui.components.RemainingAttemptBanner
import com.image.word.converter.convert.docx.ui.theme.DarkSurfaceVariant
import com.image.word.converter.convert.docx.ui.theme.LightSurfaceVariant
import com.image.word.converter.convert.docx.util.DailyAttemptManager

@Composable
fun HomeTabScreen(
    attemptManager: DailyAttemptManager,
    isSubscribed: Boolean,
    onOpenSubscription: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
    onUrl: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RemainingAttemptBanner(
            attemptManager = attemptManager,
            isSubscribed = isSubscribed,
            onPremium = onOpenSubscription,
        )

        Column(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            HomeBigCard(
                image = R.drawable.add_gallery_icon,
                title = stringResource(R.string.home_gallery_title),
                subtitle = stringResource(R.string.home_gallery_subtitle),
                startColor = Color(0xFFFFFFFF),
                endColor = Color(0xFFFFEBF7),
                onClick = onGallery,
            )
            HomeBigCard(
                image = R.drawable.add_camera_icon,
                title = stringResource(R.string.home_camera_title),
                subtitle = stringResource(R.string.home_camera_subtitle),
                startColor = Color(0xFFFFFFFF),
                endColor = Color(0xFFE3FEFF),
                onClick = onCamera,
            )

            Column {
                Text(
                    text = stringResource(R.string.more_import_options),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Text(
                    text = stringResource(R.string.more_import_options_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MiniOptionCard(
                    icon = "ic_drive.svg",
                    title = stringResource(R.string.g_drive),
                    onClick = onFiles,
                    modifier = Modifier.weight(1f),
                    startColor = Color(if(isDark) 0xFF1E2C44 else 0xFFF3F7FF),
                    endColor = Color(if(isDark) 0xFF16243A else 0xFFD3E3FF)
                )
                MiniOptionCard(
                    icon = "ic_url.svg",
                    title = stringResource(R.string.url_link),
                    onClick = onUrl,
                    modifier = Modifier.weight(1f),
                    startColor = Color(if(isDark) 0xFF3E3519 else 0xFFFFFDEB),
                    endColor = Color(if(isDark) 0xFF2E280F else 0xFFFFF9C8)
                )
                MiniOptionCard(
                    icon = "ic_file.svg",
                    title = stringResource(R.string.files),
                    onClick = onFiles,
                    modifier = Modifier.weight(1f),
                    startColor = Color(if(isDark) 0xFF173524 else 0xFFEBFFE4),
                    endColor = Color(if(isDark) 0xFF10291B else 0xFFD9FFCC)
                )
            }
        }
    }
}

@Composable
private fun HomeBigCard(
    @DrawableRes image: Int,
    title: String,
    subtitle: String,
    startColor:Color,
    endColor:Color,
    onClick: () -> Unit,
) {

    Row(
        modifier = Modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(startColor, endColor),
                    start = Offset(19.9231f, 0f),
                    end = Offset(117.9f, 172.718f)
                )
            )
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(modifier = Modifier
            .weight(1f)
            .padding(8.dp)){
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
                color = Color.Black
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkSurfaceVariant,
            )
        }
        Image(
            painter = painterResource(image),
            contentDescription = null,
            modifier = Modifier.size(80.dp)
                .offset(y = 20.dp),
        )
    }
}
