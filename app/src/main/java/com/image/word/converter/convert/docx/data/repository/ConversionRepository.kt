package com.image.word.converter.convert.docx.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.image.word.converter.convert.docx.data.local.db.AppDatabase
import com.image.word.converter.convert.docx.data.local.db.ConvertedItem
import com.image.word.converter.convert.docx.data.remote.ConversionRequest
import com.image.word.converter.convert.docx.data.remote.ImageToWordApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ConversionRepository(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = AppDatabase.getDatabase(context)
    private val convertedDao = db.convertedDao()

    private val api: ImageToWordApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://us-central1-converter-api-project.cloudfunctions.net/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ImageToWordApi::class.java)
    }

    suspend fun convertAndSave(imageUri: String): Result<ConvertedItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = ensureAuth()
            val base64Image = encodeImageToBase64(imageUri)
            val response = api.convertImage("Bearer $token", ConversionRequest(base64Image))

            if (response.isSuccessful) {
                val body = response.body()
                val fileUrl = body?.result?.file 
                    ?: body?.file 
                    ?: body?.url 
                    ?: body?.data?.file
                    ?: throw Exception("Invalid response format")

                val newItem = ConvertedItem(
                    fileName = "Word_${System.currentTimeMillis() / 1000}",
                    fileUrl = fileUrl
                )
                convertedDao.insertItem(newItem)
                Result.success(newItem)
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureAuth(): String {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        return user?.getIdToken(true)?.await()?.token ?: throw Exception("Auth failed")
    }

    private fun encodeImageToBase64(uriString: String): String {
        val uri = Uri.parse(uriString)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Could not open input stream")
        val bitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw Exception("Could not decode bitmap")
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
