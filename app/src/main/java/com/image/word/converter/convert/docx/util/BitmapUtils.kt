package com.image.word.converter.convert.docx.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object BitmapUtils {
    fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val config = src.config ?: Bitmap.Config.ARGB_8888
        val dest = Bitmap.createBitmap(src.width, src.height, config)
        val canvas = Canvas(dest)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    fun applySaturation(src: Bitmap, value: Float): Bitmap {
        val cm = ColorMatrix().apply { setSaturation(value) }
        return applyColorMatrix(src, cm)
    }

    fun applyBrightness(src: Bitmap, value: Float): Bitmap {
        val cm = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, value,
                0f, 1f, 0f, 0f, value,
                0f, 0f, 1f, 0f, value,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(src, cm)
    }

    fun applyContrast(src: Bitmap, value: Float): Bitmap {
        val scale = value
        val translate = (-0.5f * scale + 0.5f) * 255f
        val cm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(src, cm)
    }

    fun applyHue(src: Bitmap, value: Float): Bitmap {
        val cm = ColorMatrix()
        cm.setRotate(0, value)
        cm.setRotate(1, value)
        cm.setRotate(2, value)
        return applyColorMatrix(src, cm)
    }
}
