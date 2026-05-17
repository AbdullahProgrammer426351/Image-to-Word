package com.image.word.converter.convert.docx.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

@Composable
fun AssetImage(
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    tint: Color? = null,
) {
    val context = LocalContext.current
    val baseName = name.substringBeforeLast('.').lowercase()
    val drawableId = context.resources.getIdentifier(baseName, "drawable", context.packageName)

    if (drawableId != 0) {
        AsyncImage(
            model = drawableId,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = tint?.let { ColorFilter.tint(it) },
        )
        return
    }

    val filePath = "file:///android_asset/images/$name"
    val isSvg = name.endsWith(".svg", ignoreCase = true)
    val request = ImageRequest.Builder(context)
        .data(filePath)
        .apply {
            if (isSvg) {
                decoderFactory(SvgDecoder.Factory())
            }
        }
        .build()

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}
