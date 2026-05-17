package com.image.word.converter.convert.docx.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConvertedDao {
    @Query("SELECT * FROM converted_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<ConvertedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ConvertedItem)

    @Delete
    suspend fun deleteItem(item: ConvertedItem)

    @Query("SELECT COUNT(*) FROM converted_items WHERE fileName = :fileName")
    suspend fun countByName(fileName: String): Int
}
