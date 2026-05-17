package com.image.word.converter.convert.docx.data

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ImageToWordApi {
    suspend fun uploadImage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val data = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            stream.toByteArray()
        }
        val base64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

        val user = FirebaseAuth.getInstance().currentUser ?: run {
            FirebaseAuth.getInstance().signInAnonymously().await().user
        } ?: return@withContext null

        val token = user.getIdToken(false).await().token ?: return@withContext null
        val url = URL("https://us-central1-converter-api-project.cloudfunctions.net/imageToWord")
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
        val body = runCatching {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            stream?.bufferedReader()?.readText().orEmpty()
        }.getOrDefault("")

        parseFileUrl(body)
    }

    private fun parseFileUrl(raw: String): String? {
        if (raw.isBlank()) return null
        runCatching {
            val json = JSONObject(raw)
            if (json.optBoolean("success")) {
                val result = json.optJSONObject("result")
                val link = result?.optString("file").orEmpty()
                if (link.isNotBlank()) return link
            }
            listOf("file", "url").forEach { key ->
                val link = json.optString(key)
                if (link.isNotBlank()) return link
            }
            val dataObj = json.optJSONObject("data")
            val nested = dataObj?.optString("file").orEmpty()
            if (nested.isNotBlank()) return nested
        }
        return null
    }
}
