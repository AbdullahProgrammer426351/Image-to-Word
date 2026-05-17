package com.image.word.converter.convert.docx.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.IosTopBar
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.ui.theme.appWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale
import com.image.word.converter.convert.docx.ads.BannerAd
import com.image.word.converter.convert.docx.util.BitmapUtils
import com.image.word.converter.convert.docx.util.bitmapToCacheUri
import com.image.word.converter.convert.docx.util.decodeBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

private data class FilterModel(
    val name: String,
    val isLocked: Boolean = false
)

@Composable
fun FilterScreen(
    index: Int,
    sessionState: SessionState,
    isSubscribed:Boolean,
    onBack: () -> Unit,
    onOpenSubscription: () -> Unit
) {
    val context = LocalContext.current
    val state by sessionState.uiState.collectAsState()
    val originalBitmap = state.selectedImageUris.getOrNull(index)?.let { decodeBitmap(context.contentResolver, it) }

    if (originalBitmap == null) {
        SideEffect { onBack() }
        return
    }

    val filters = listOf(
        FilterModel(stringResource(R.string.none)),
        FilterModel(stringResource(R.string.high_saturation)),
        FilterModel(stringResource(R.string.low_saturation)),
        FilterModel(stringResource(R.string.high_hue), true),
        FilterModel(stringResource(R.string.blue_tint), true),
        FilterModel(stringResource(R.string.light_dark), true),
        FilterModel(stringResource(R.string.light_light), true),
        FilterModel(stringResource(R.string.green_tint), true),
        FilterModel(stringResource(R.string.sepia), true),
        FilterModel(stringResource(R.string.vibrance), true)
    )

    var selectedIndex by remember { mutableIntStateOf(0) }
    var filteredBitmap by remember { mutableStateOf(originalBitmap) }
    val previewCache = remember { mutableStateMapOf<Int, Bitmap>() }

    fun applySepia(src: Bitmap): Bitmap {
        val cm = ColorMatrix().apply {
            setScale(1f, 1f, 0.8f, 1f)
            postConcat(ColorMatrix(floatArrayOf(
                0.393f,0.769f,0.189f,0f,0f,
                0.349f,0.686f,0.168f,0f,0f,
                0.272f,0.534f,0.131f,0f,0f,
                0f,0f,0f,1f,0f
            )))
        }
        return BitmapUtils.applyColorMatrix(src, cm)
    }

    fun applyFilter(index: Int, src: Bitmap): Bitmap {
        return when (index) {
            1 -> BitmapUtils.applySaturation(src, 2f) // High Saturation
            2 -> BitmapUtils.applySaturation(src, 0.5f) // Low Saturation
            3 -> BitmapUtils.applyHue(src, 180f) // High Hue
            4 -> BitmapUtils.applyColorMatrix(src, ColorMatrix().apply {
                set(floatArrayOf(
                    1f,0f,0f,0f,0f,
                    0f,1f,0f,0f,0f,
                    0f,0f,1.5f,0f,0f,
                    0f,0f,0f,1f,0f
                ))
            }) // Blue tint
            5 -> BitmapUtils.applyBrightness(src, -50f) // Dark
            6 -> BitmapUtils.applyBrightness(src, 50f) // Light
            7 -> BitmapUtils.applyColorMatrix(src, ColorMatrix().apply {
                set(floatArrayOf(
                    1f,0f,0f,0f,0f,
                    0f,1.5f,0f,0f,0f,
                    0f,0f,1f,0f,0f,
                    0f,0f,0f,1f,0f
                ))
            }) // Green tint
            8 -> applySepia(src)
            9 -> BitmapUtils.applySaturation(src, 1.8f) // Vibrance approx
            else -> src
        }
    }

    LaunchedEffect(selectedIndex) {
        withContext(Dispatchers.Default) {
            val result = applyFilter(selectedIndex, originalBitmap)
            withContext(Dispatchers.Main) {
                filteredBitmap = result
            }
        }
    }

    LaunchedEffect(originalBitmap) {
        val thumb = originalBitmap.scale(100, 100)

        filters.indices.map { index ->
            async(Dispatchers.Default) {
                val preview = applyFilter(index, thumb)
                index to preview
            }
        }.awaitAll().forEach { (index, bmp) ->
            previewCache[index] = bmp
        }
    }

//    LaunchedEffect(selectedFilter) {
//        filteredBitmap = when (selectedFilter) {
//            "Gray" -> toGray(originalBitmap)
//            "B&W" -> toGray(originalBitmap) // Simplified for now
//            else -> originalBitmap
//        }
//    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        IosTopBar(
            title = stringResource(R.string.filters),
            onBack = onBack,
            onAction = {
                sessionState.updateImage(index, bitmapToCacheUri(context, filteredBitmap))
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
            Image(
                bitmap = filteredBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier
            .background(appWhite())
            .horizontalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 20.dp)
            .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.width(20.dp))

                    filters.forEachIndexed { index, filter ->
                        val preview = previewCache[index]

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                if (filter.isLocked && !isSubscribed) {
                                    onOpenSubscription()
                                    return@clickable
                                }
                                selectedIndex = index
                            }
                        ) {
                            Box {
                                if (preview != null) {
                                    Image(
                                        bitmap = preview.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = 2.dp,
                                                color = if (selectedIndex == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(Color.Gray, RoundedCornerShape(10.dp))
                                    )
                                }

                                if (filter.isLocked && isSubscribed) {
                                    Text(
                                        "🔒",
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    )
                                }
                            }

                            Text(
                                text = filter.name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))
                }
                BannerAd(isSubscribed)
        }
    }



}

@Composable
private fun FilterItem(label: String, image: Bitmap, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        androidx.compose.foundation.Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .background(
                    if (selected) Color(0xFF4CAF50) else Color.LightGray,
                    MaterialTheme.shapes.medium
                )
                .padding(if (selected) 2.dp else 0.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
            color = if (selected) Color(0xFF4CAF50) else Color.Black
        )
    }
}

private fun toGray(source: Bitmap): Bitmap {
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return output
}
