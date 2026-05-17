package com.image.word.converter.convert.docx.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "converted_items")
data class ConvertedItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileUrl: String,
    val createdAt: Long = System.currentTimeMillis()
)
