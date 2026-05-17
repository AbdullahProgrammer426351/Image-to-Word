package com.image.word.converter.convert.docx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.theme.appWhite
import java.io.File

@Composable
fun WordMainTopBar(
    showPremium: Boolean,
    onPremiumClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showPremium) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPremiumClick),
                contentAlignment = Alignment.Center,
            ) {
                AssetImage(name = "ic_dimond_h.png", modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
fun MainTopBar(
    title: String,
    showPremium: Boolean,
    onPremiumClick: () -> Unit,
    showSearch: Boolean,
    onSearchClick: () -> Unit,
    profileImagePath: String?,
    onProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showPremium) {
                AssetImage(
                    name = "icCrown.svg",
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onPremiumClick)
                )
            }
            if (showSearch) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onSearchClick),
                    contentAlignment = Alignment.Center,
                ) {
                    AssetImage(name = "search_icon.png", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Box(
                modifier = Modifier
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center,
            ) {
                val imagePath = profileImagePath?.takeIf { File(it).exists() }
                if (imagePath != null) {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = stringResource(R.string.profile),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    AssetImage(name = "profile_icon.svg", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun ChildTopBar(
    title: String,
    onBack: () -> Unit,
    showBack: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onBack),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Box(modifier = Modifier.size(28.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            action?.invoke()
        }
    }
}

@Composable
fun IosTopBar(
    title: String,
    onBack: () -> Unit,
    onAction: (() -> Unit)? = null,
    isCloseIcon: Boolean = false,
    actionIcon: @Composable (() -> Unit)? = { Icon(Icons.Default.Check, null,tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(45.dp)
                .background(appWhite(), CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            if (isCloseIcon) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp).weight(1f)
        )

        if (onAction != null) {
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .background(appWhite(), CircleShape)
                    .clickable { onAction() },
                contentAlignment = Alignment.Center
            ) {
                actionIcon?.invoke()
            }
        } else {
            Spacer(modifier = Modifier.size(45.dp))
        }
    }
}

@Composable
fun PremiumBanner(
    visible: Boolean,
    onClick: () -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    com.image.word.converter.convert.docx.ui.theme.WordGradients.subscribeButton,
                ),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AssetImage(name = "icCrown.svg", modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.go_premium),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.premium_features_fully_unlocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.unlock),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}
