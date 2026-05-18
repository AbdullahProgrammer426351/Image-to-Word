package com.image.word.converter.convert.docx.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ads.BannerAd
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.components.IosTopBar
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.ui.theme.appWhite
import com.image.word.converter.convert.docx.util.BitmapUtils
import com.image.word.converter.convert.docx.util.bitmapToCacheUri
import com.image.word.converter.convert.docx.util.decodeBitmap

@Composable
fun AdjustScreen(
    index: Int,
    sessionState: SessionState,
    isSubscribed: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by sessionState.uiState.collectAsState()
    val originalBitmap = remember(state.selectedImageUris, index) {
        state.selectedImageUris.getOrNull(index)?.let { decodeBitmap(context.contentResolver, it) }
    }
    
    if (originalBitmap == null) {
        SideEffect { onBack() }
        return
    }

    var adjustedBitmap by remember { mutableStateOf(originalBitmap) }
    
    var sliderValue by remember { mutableFloatStateOf(1.0f) }
    var selectedAdjust by remember { mutableStateOf("Brightness") }

    LaunchedEffect(sliderValue, selectedAdjust) {
        val factor = if (selectedAdjust == "Brightness") (sliderValue - 1f) * 100f else sliderValue
        adjustedBitmap = when (selectedAdjust) {
            "Brightness" -> BitmapUtils.applyBrightness(originalBitmap, factor)
            "Contrast" -> BitmapUtils.applyContrast(originalBitmap, sliderValue)
            "Saturation" -> BitmapUtils.applySaturation(originalBitmap, sliderValue)
            "Hue" -> BitmapUtils.applyHue(originalBitmap, (sliderValue - 1f) * 180f)
            else -> originalBitmap
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        IosTopBar(
            title = stringResource(R.string.adjust),
            onBack = onBack,
            onAction = {
                sessionState.updateImage(index, bitmapToCacheUri(context, adjustedBitmap))
                onBack()
            },
            isCloseIcon = true
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bitmap = adjustedBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(appWhite())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF4CAF50)
                    )
                )
                Text(
                    text = (sliderValue * 128).toInt().toString(),
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AdjustOptionItem(icon = "brightness_icon.png", label = stringResource(R.string.brightness), selected = selectedAdjust == "Brightness") { selectedAdjust = "Brightness" }
                AdjustOptionItem(icon = "contrast_icon.png", label = stringResource(R.string.contrast), selected = selectedAdjust == "Contrast") { selectedAdjust = "Contrast" }
                AdjustOptionItem(icon = "saturation_icon.png", label = stringResource(R.string.saturation), selected = selectedAdjust == "Saturation") { selectedAdjust = "Saturation" }
                AdjustOptionItem(icon = "color_icon.png", label = stringResource(R.string.hue), selected = selectedAdjust == "Hue") { selectedAdjust = "Hue" }
            }

            BannerAd(isSubscribed)
        }
    }
}

@Composable
private fun AdjustOptionItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        AssetImage(name = icon, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color(0xFF4CAF50) else Color.Gray,
            fontWeight = FontWeight.Medium
        )
    }
}
