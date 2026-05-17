package com.image.word.converter.convert.docx.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

private const val MAX_BITMAP_DIMENSION = 4096

fun decodeBitmap(contentResolver: ContentResolver, uri: Uri): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        var inSampleSize = 1
        while (options.outWidth / inSampleSize > MAX_BITMAP_DIMENSION || options.outHeight / inSampleSize > MAX_BITMAP_DIMENSION) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun decodeBitmapFromFile(path: String): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        var inSampleSize = 1
        while (options.outWidth / inSampleSize > MAX_BITMAP_DIMENSION || options.outHeight / inSampleSize > MAX_BITMAP_DIMENSION) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        BitmapFactory.decodeFile(path, decodeOptions)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun mergeImagesVertically(images: List<Bitmap>): Bitmap {
    if (images.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    
    val maxWidth = images.maxOf { it.width }.coerceAtMost(MAX_BITMAP_DIMENSION)
    var totalHeight = images.sumOf { image ->
        val scale = maxWidth.toFloat() / image.width.toFloat()
        (image.height * scale).toInt()
    }
    
    // If total height exceeds limit, scale everything down
    var finalScale = 1f
    if (totalHeight > MAX_BITMAP_DIMENSION) {
        finalScale = MAX_BITMAP_DIMENSION.toFloat() / totalHeight.toFloat()
        totalHeight = MAX_BITMAP_DIMENSION
    }

    val merged = Bitmap.createBitmap(
        (maxWidth * finalScale).toInt().coerceAtLeast(1), 
        totalHeight.coerceAtLeast(1), 
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(merged)
    var yOffset = 0f
    
    images.forEach { image ->
        val scaleToMaxWidth = maxWidth.toFloat() / image.width.toFloat()
        val targetWidth = (image.width * scaleToMaxWidth * finalScale)
        val targetHeight = (image.height * scaleToMaxWidth * finalScale)
        val rect = android.graphics.RectF(0f, yOffset, targetWidth, yOffset + targetHeight)
        canvas.drawBitmap(image, null, rect, null)
        yOffset += targetHeight
    }
    return merged
}

fun bitmapToCacheUri(context: Context, bitmap: Bitmap): Uri {
    val file = File(context.cacheDir, "img_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    return Uri.fromFile(file)
}

fun createCameraCaptureUri(context: Context): Uri {
    val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
    file.parentFile?.mkdirs()
    if (!file.exists()) {
        file.createNewFile()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}
