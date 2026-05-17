package com.image.word.converter.convert.docx.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.model.ConvertedItem
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WordFileHelper {
    suspend fun downloadDocx(context: Context, item: ConvertedItem): File? = withContext(Dispatchers.IO) {
        if (item.fileUrl.isBlank()) return@withContext null
        runCatching {
            val connection = URL(item.fileUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 120_000
            connection.readTimeout = 120_000
            connection.inputStream.use { input ->
                val outDir = File(context.filesDir, "word_downloads").apply { mkdirs() }
                val outFile = File(outDir, "${item.fileName}.docx")
                outFile.outputStream().use { output -> input.copyTo(output) }
                outFile
            }
        }.getOrNull()
    }

    suspend fun shareWord(context: Context, item: ConvertedItem): Boolean {
        val file = downloadDocx(context, item) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
        return true
    }

    suspend fun exportWord(context: Context, item: ConvertedItem): Boolean {
        val file = downloadDocx(context, item) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.export)))
            true
        }.getOrDefault(false)
    }
}
