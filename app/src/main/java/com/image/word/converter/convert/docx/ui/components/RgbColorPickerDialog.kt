package com.image.word.converter.convert.docx.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.image.word.converter.convert.docx.R

@Composable
fun RgbColorPickerDialog(
    initialColor: Int,
    showAlphaSlider: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val controller = rememberColorPickerController()
    var selectedColor by remember(initialColor) { mutableStateOf(Color(initialColor)) }

    LaunchedEffect(initialColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColor, hsv)

        // Avoid "stuck on black" behavior: when HSV value is 0, the library keeps emitting black
        // until brightness changes. We keep selectedColor as requested, but seed controller with
        // a visible color so hue/saturation picking works immediately.
        val seedColor =
            if (hsv[2] <= 0f) {
                Color.White
            } else {
                Color(initialColor)
            }
        controller.selectByColor(seedColor, fromUser = false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    controller = controller,
                    onColorChanged = { envelope ->
                        selectedColor = envelope.color
                    },
                )

                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    controller = controller,
                )

                if (showAlphaSlider) {
                    AlphaSlider(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        controller = controller,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .background(selectedColor, RoundedCornerShape(8.dp))
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val colorInt = selectedColor.toArgb()
                val finalColor = if (showAlphaSlider) colorInt else (colorInt or 0xFF000000.toInt())
                onConfirm(finalColor)
            }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
