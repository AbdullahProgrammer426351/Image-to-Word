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
    private val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".provider"

    private fun getLocalFile(context: Context, item: ConvertedItem): File? {
        val localPath = item.localFilePath ?: return null
        val file = File(localPath)
        if (!file.exists()) return null
        return file
    }

    suspend fun downloadDocx(context: Context, item: ConvertedItem): File? = withContext(Dispatchers.IO) {
        getLocalFile(context, item) ?: run {
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
    }

    suspend fun downloadToStorage(context: Context, item: ConvertedItem): String? = withContext(Dispatchers.IO) {
        if (item.fileUrl.isBlank()) return@withContext null
        val dir = File(context.filesDir, "converted").also { it.mkdirs() }
        val file = File(dir, "${item.fileName}.docx")
        runCatching {
            val connection = URL(item.fileUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        }.getOrNull()
    }

    fun fileUriFromLocal(context: Context, path: String): android.net.Uri? {
        val file = File(path)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX", file)
    }

    suspend fun shareWord(context: Context, item: ConvertedItem): Boolean {
        val file = downloadDocx(context, item) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_DOCX
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
        return true
    }

    suspend fun exportWord(context: Context, item: ConvertedItem): Boolean {
        val file = downloadDocx(context, item) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_DOCX)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.export)))
            true
        }.getOrDefault(false)
    }

    suspend fun previewWord(context: Context, item: ConvertedItem): Boolean {
        val file = downloadDocx(context, item) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_DOCX)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, null))
            true
        }.getOrDefault(false)
    }
}
