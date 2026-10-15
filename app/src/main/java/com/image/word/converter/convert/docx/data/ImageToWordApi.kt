package com.image.word.converter.convert.docx.data

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ImageToWordApi {
    suspend fun uploadImage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val data = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                stream.toByteArray()
            }
            val base64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

            val user = FirebaseAuth.getInstance().currentUser ?: run {
                FirebaseAuth.getInstance().signInAnonymously().await().user
            } ?: return@withContext null

            val token = user.getIdToken(false).await().token ?: return@withContext null
            val url = URL("https://us-central1-converter-api-project.cloudfunctions.net/imageToWordfree")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 120_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }

            val payload = JSONObject().put("image", base64).toString()
            connection.outputStream.use { it.write(payload.toByteArray()) }
            try {
                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (responseCode !in 200..299) {
                    return@withContext null
                }

                parseFileUrl(body)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseFileUrl(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)

            // The Cloud Function wraps the upstream response in `result`. The new
            // endpoint may return that result as either a URL string or a JSON object.
            findFileUrl(json.opt("result")) ?: findFileUrl(json)
        }.getOrNull()
    }

    private fun findFileUrl(value: Any?): String? = when (value) {
        is String -> value.takeIf(::isHttpUrl)
        is JSONObject -> {
            FILE_URL_KEYS.firstNotNullOfOrNull { key ->
                findFileUrl(value.opt(key))
            } ?: value.keys().asSequence().firstNotNullOfOrNull { key ->
                findFileUrl(value.opt(key))
            }
        }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { index ->
            findFileUrl(value.opt(index))
        }
        else -> null
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)

    private companion object {
        val FILE_URL_KEYS = listOf(
            "file",
            "url",
            "fileUrl",
            "file_url",
            "downloadUrl",
            "download_url",
            "data",
        )
    }
}
