package com.image.word.converter.convert.docx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.ui.components.GradientButton
import com.image.word.converter.convert.docx.ui.components.IosTopBar
import com.image.word.converter.convert.docx.ui.state.SessionState
import com.image.word.converter.convert.docx.util.bitmapToCacheUri
import com.image.word.converter.convert.docx.util.decodeBitmap

@Composable
fun TextOverlayScreen(
    index: Int,
    sessionState: SessionState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by sessionState.uiState.collectAsState()
    val originalBitmap = remember(state.selectedImageUris, index) {
        state.selectedImageUris.getOrNull(index)?.let { decodeBitmap(context.contentResolver, it) }
    }
    if (originalBitmap == null) {
        onBack()
        return
    }

    var text by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        IosTopBar(
            title = stringResource(R.string.text),
            onBack = onBack,
            onAction = {
                if (text.isNotBlank()) {
                    val out = originalBitmap.copy(originalBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, true)
                    val canvas = android.graphics.Canvas(out)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = out.width * 0.06f
                        isAntiAlias = true
                        setShadowLayer(8f, 2f, 2f, android.graphics.Color.BLACK)
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    canvas.drawText(text, out.width / 2f, out.height * 0.88f, paint)
                    sessionState.updateImage(index, bitmapToCacheUri(context, out))
                }
                onBack()
            },
            isCloseIcon = true,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                bitmap = originalBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.enter_text)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            GradientButton(
                text = stringResource(R.string.apply_text),
                onClick = {
                    if (text.isNotBlank()) {
                        val out = originalBitmap.copy(originalBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, true)
                        val canvas = android.graphics.Canvas(out)
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = out.width * 0.06f
                            isAntiAlias = true
                            setShadowLayer(8f, 2f, 2f, android.graphics.Color.BLACK)
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        canvas.drawText(text, out.width / 2f, out.height * 0.88f, paint)
                        sessionState.updateImage(index, bitmapToCacheUri(context, out))
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
