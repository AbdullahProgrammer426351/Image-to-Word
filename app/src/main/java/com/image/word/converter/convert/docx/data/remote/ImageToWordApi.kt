package com.image.word.converter.convert.docx.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ImageToWordApi {
    @POST("imageToWord")
    suspend fun convertImage(
        @Header("Authorization") token: String,
        @Body request: ConversionRequest
    ): Response<ConversionResponse>
}

data class ConversionRequest(
    val image: String // Base64 encoded image
)

data class ConversionResponse(
    val success: Boolean?,
    val result: ConversionResult?,
    val file: String?,
    val url: String?,
    val data: ConversionData?
)

data class ConversionResult(
    val file: String?
)

data class ConversionData(
    val file: String?
)
