package com.image.word.converter.convert.docx.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.components.IosTopBar
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.ui.theme.appWhite
import com.image.word.converter.convert.docx.util.bitmapToCacheUri
import com.image.word.converter.convert.docx.util.decodeBitmap

data class HighlighterPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val canvasSize: IntSize
)

@Composable
fun HighlighterScreen(
    index: Int,
    sessionState: SessionState,
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

    var paths by remember { mutableStateOf(listOf<HighlighterPath>()) }
    var currentPathPoints by remember { mutableStateOf<List<Offset>?>(null) }
    
    val colors = listOf(
        Color(0x66FFFF00), // Yellow
        Color(0x6600FF00), // Green
        Color(0x6600FFFF), // Cyan
        Color(0x66FF00FF), // Magenta
        Color(0x66FF0000), // Red
        Color(0x66000000), // Black/Dark
    )
    var selectedColor by remember { mutableStateOf(colors[0]) }
    var strokeWidth by remember { mutableFloatStateOf(40f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        IosTopBar(
            title = stringResource(R.string.highlighter),
            onBack = onBack,
            onAction = {
                val result = applyPathsToBitmap(originalBitmap, paths)
                sessionState.updateImage(index, bitmapToCacheUri(context, result))
                onBack()
            },
            isCloseIcon = true
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(originalBitmap.width.toFloat() / originalBitmap.height.toFloat())
                    .onGloballyPositioned { boxSize = it.size }
                    .pointerInput(boxSize) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (boxSize.width > 0 && boxSize.height > 0) {
                                    currentPathPoints = listOf(offset)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (boxSize.width > 0 && boxSize.height > 0) {
                                    currentPathPoints = currentPathPoints?.plus(change.position)
                                }
                            },
                            onDragEnd = {
                                currentPathPoints?.let { points ->
                                    paths = paths + HighlighterPath(points, selectedColor, strokeWidth, boxSize)
                                }
                                currentPathPoints = null
                            }
                        )
                    }
            ) {
                androidx.compose.foundation.Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    paths.forEach { hPath ->
                        if (hPath.points.size > 1) {
                            val path = Path().apply {
                                moveTo(hPath.points.first().x, hPath.points.first().y)
                                hPath.points.drop(1).forEach { p -> lineTo(p.x, p.y) }
                            }
                            drawPath(
                                path = path,
                                color = hPath.color,
                                style = Stroke(
                                    width = hPath.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    currentPathPoints?.let { points ->
                        if (points.size > 1) {
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { p -> lineTo(p.x, p.y) }
                            }
                            drawPath(
                                path = path,
                                color = selectedColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(appWhite())
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(color.copy(alpha = 1f), shape = MaterialTheme.shapes.small)
                                .clickable { selectedColor = color }
                                .padding(2.dp)
                        ) {
                            if (selectedColor.copy(alpha = 1f) == color.copy(alpha = 1f)) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.4f)))
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AssetImage(
                        name = "undo_icon.png",
                        modifier = Modifier.size(26.dp).clickable {
                            if (paths.isNotEmpty()) paths = paths.dropLast(1)
                        }
                    )
                    AssetImage(
                        name = "delete_icon.png",
                        modifier = Modifier.size(26.dp).clickable {
                            paths = emptyList()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.highlighter), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Slider(
                    value = strokeWidth,
                    onValueChange = { strokeWidth = it },
                    valueRange = 5f..80f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4CAF50),
                        activeTrackColor = Color(0xFF4CAF50)
                    )
                )
            }
        }
    }
}

private fun applyPathsToBitmap(original: Bitmap, paths: List<HighlighterPath>): Bitmap {
    if (paths.isEmpty()) return original
    val result = original.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(result)

    paths.forEach { hPath ->
        if (hPath.points.size > 1 && hPath.canvasSize.width > 0) {
            val scaleX = original.width.toFloat() / hPath.canvasSize.width
            val scaleY = original.height.toFloat() / hPath.canvasSize.height
            
            val paint = AndroidPaint().apply {
                color = hPath.color.toArgb()
                strokeWidth = hPath.strokeWidth * scaleX
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                isAntiAlias = true
            }
            val path = AndroidPath()
            path.moveTo(hPath.points.first().x * scaleX, hPath.points.first().y * scaleY)
            hPath.points.drop(1).forEach { p ->
                path.lineTo(p.x * scaleX, p.y * scaleY)
            }
            canvas.drawPath(path, paint)
        }
    }
    return result
}
