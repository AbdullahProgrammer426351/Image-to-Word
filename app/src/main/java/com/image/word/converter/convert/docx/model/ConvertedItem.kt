package com.image.word.converter.convert.docx.model

data class ConvertedItem(
    val id: String,
    val fileName: String,
    val fileUrl: String,
    val createdAt: Long,
    val imagePath: String = "",
)
