package com.image.word.converter.convert.docx.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.scale
import coil.compose.AsyncImage
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ads.BannerAd
import com.image.word.converter.convert.docx.ui.components.AssetImage
import com.image.word.converter.convert.docx.ui.components.ChildTopBar
import com.image.word.converter.convert.docx.ui.components.RgbColorPickerDialog
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.util.bitmapToCacheUri
import com.image.word.converter.convert.docx.util.decodeBitmap
import kotlinx.coroutines.launch

@Composable
fun SignatureOverlayScreen(
    index: Int,
    sessionState: SessionState,
    isSubscribed: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by sessionState.uiState.collectAsState()
    val imageUri = remember(state.selectedImageUris, index) { state.selectedImageUris.getOrNull(index) }

    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signatureOpacity by remember { mutableStateOf(1f) }
    var showPad by remember { mutableStateOf(false) }

    if (imageUri == null) {
        onBack()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding())
    ) {
        ChildTopBar(title = stringResource(R.string.signature), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (signatureBitmap != null) {
                    Image(
                        bitmap = signatureBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(0.5f),
                        alpha = signatureOpacity,
                    )
                }
            }

            if (signatureBitmap != null) {
                Text(stringResource(R.string.opacity_label), color = MaterialTheme.colorScheme.onSurface)
                Slider(value = signatureOpacity, onValueChange = { signatureOpacity = it }, valueRange = 0.1f..1f)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showPad = true }, modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(if (signatureBitmap == null) R.string.add_signature else R.string.replace_signature),
                        color = ComposeColor.White
                    )
                }
                Button(
                    onClick = { signatureBitmap = null },
                    enabled = signatureBitmap != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.delete_signature), color = ComposeColor.White)
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            val base = decodeBitmap(context.contentResolver, imageUri) ?: return@runCatching null
                            val signed = applySignature(base, signatureBitmap!!, signatureOpacity)
                            sessionState.updateImage(index, bitmapToCacheUri(context, signed))
                        }.onSuccess {
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = signatureBitmap != null,
            ) {
                Text(stringResource(R.string.apply_signature), color = ComposeColor.White)
            }
        }

        BannerAd(isSubscribed = isSubscribed)
    }

    if (showPad) {
        SignaturePadDialog(
            onDismiss = { showPad = false },
            onSave = { bitmap ->
                signatureBitmap = bitmap
                showPad = false
            },
        )
    }
}

private data class SignatureStroke(
    val points: SnapshotStateList<Offset>,
    val width: Float,
    val color: Int,
    val isEraser: Boolean,
)

@Composable
private fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    val strokes = remember { mutableStateListOf<SignatureStroke>() }
    val redoStrokes = remember { mutableStateListOf<SignatureStroke>() }
    var currentStroke by remember { mutableStateOf<SnapshotStateList<Offset>?>(null) }
    var currentSize by remember { mutableStateOf(IntSize(900, 500)) }
    var penSize by remember { mutableStateOf(3f) }
    var penColor by remember { mutableStateOf(android.graphics.Color.BLACK) }
    var isEraserActive by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularTopActionButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                Text(
                    text = stringResource(R.string.draw_signature),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                CircularTopActionButton(
                    icon = Icons.Default.Check,
                    contentDescription = stringResource(R.string.save),
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            onSave(renderSignatureBitmap(strokes, currentSize.width, currentSize.height))
                        }
                    },
                    enabled = strokes.isNotEmpty()
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ComposeColor.White)
                    .onSizeChanged { currentSize = it }
                    .pointerInput(isEraserActive, penSize, penColor) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val newPoints = mutableStateListOf(offset)
                                currentStroke = newPoints
                                strokes.add(SignatureStroke(newPoints, penSize, penColor, isEraserActive))
                                redoStrokes.clear()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke?.add(change.position)
                            },
                            onDragEnd = {
                                currentStroke = null
                            }
                        )
                    }
            ) {
                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                    strokes.forEach { stroke ->
                        val strokeColor = ComposeColor(stroke.color)
                        for (i in 1 until stroke.points.size) {
                            drawLine(
                                color = strokeColor,
                                start = stroke.points[i - 1],
                                end = stroke.points[i],
                                strokeWidth = stroke.width,
                            )
                        }
                        if (stroke.points.size == 1) {
                            drawCircle(
                                color = strokeColor,
                                radius = stroke.width / 2f,
                                center = stroke.points.first(),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = penSize,
                        onValueChange = { penSize = it },
                        valueRange = 1f..20f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.surface,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top,
                ) {
                    SignatureToolButton(
                        label = stringResource(R.string.eraser),
                        onClick = { isEraserActive = !isEraserActive },
                        labelColor = if (isEraserActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    ) {
                        AssetImage(
                            name = "eraser_icon.png",
                            modifier = Modifier.size(26.dp),
                            tint = if (isEraserActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    SignatureToolButton(
                        label = stringResource(R.string.undo),
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                redoStrokes.add(strokes.removeAt(strokes.lastIndex))
                            }
                        },
                        enabled = strokes.isNotEmpty(),
                    ) {
                        AssetImage(name = "undo_icon.png", modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    SignatureToolButton(
                        label = stringResource(R.string.redo),
                        onClick = {
                            if (redoStrokes.isNotEmpty()) {
                                strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
                            }
                        },
                        enabled = redoStrokes.isNotEmpty(),
                    ) {
                        AssetImage(
                            name = "undo_icon.png",
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer(scaleX = -1f),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    SignatureToolButton(
                        label = stringResource(R.string.clear),
                        onClick = {
                            strokes.clear()
                            redoStrokes.clear()
                        },
                        enabled = strokes.isNotEmpty(),
                    ) {
                        AssetImage(name = "delete_icon.png", modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    SignatureToolButton(
                        label = stringResource(R.string.color),
                        onClick = {
                            isEraserActive = false
                            showColorPicker = true
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ComposeColor(penColor))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape),
                        )
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        RgbColorPickerDialog(
            initialColor = penColor,
            showAlphaSlider = false,
            onDismiss = { showColorPicker = false },
            onConfirm = { selected ->
                penColor = selected
                isEraserActive = false
                showColorPicker = false
            },
        )
    }
}

@Composable
private fun CircularTopActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(if (enabled) 1f else 0.45f)
            )
        }
    }
}

@Composable
private fun SignatureToolButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    labelColor: ComposeColor = MaterialTheme.colorScheme.onSurface,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
        )
    }
}

private fun renderSignatureBitmap(
    strokes: List<SignatureStroke>,
    width: Int,
    height: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    strokes.forEach { stroke ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = stroke.color
            strokeWidth = stroke.width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (stroke.isEraser) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }

        if (stroke.points.size == 1) {
            canvas.drawCircle(stroke.points.first().x, stroke.points.first().y, stroke.width / 2f, paint)
        } else {
            for (i in 1 until stroke.points.size) {
                canvas.drawLine(
                    stroke.points[i - 1].x,
                    stroke.points[i - 1].y,
                    stroke.points[i].x,
                    stroke.points[i].y,
                    paint,
                )
            }
        }
    }

    return bitmap
}

private fun applySignature(base: Bitmap, signature: Bitmap, opacity: Float): Bitmap {
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val scaledWidth = (base.width * 0.35f).toInt().coerceAtLeast(1)
    val aspect = signature.height.toFloat() / signature.width.coerceAtLeast(1)
    val scaledHeight = (scaledWidth * aspect).toInt().coerceAtLeast(1)
    val scaled = signature.scale(scaledWidth, scaledHeight)
    val left = (base.width - scaledWidth) / 2f
    val top = (base.height - scaledHeight) / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        isFilterBitmap = true
    }
    canvas.drawBitmap(scaled, left, top, paint)
    return result
}
