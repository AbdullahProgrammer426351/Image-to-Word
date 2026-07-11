package com.image.word.converter.convert.docx.data

import android.content.Context
import android.graphics.Bitmap
import com.image.word.converter.convert.docx.model.ConvertedItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class ConvertedRepository(private val context: Context) {
    private val itemsFile = File(context.filesDir, "converted_items.json")
    private val imagesDir = File(context.filesDir, "converted_images").apply { mkdirs() }
    val convertedDir: File = File(context.filesDir, "converted").also { it.mkdirs() }

    fun list(): List<ConvertedItem> {
        val raw = readItemsArray()
        val items = buildList {
            for (i in 0 until raw.length()) {
                val obj = raw.optJSONObject(i) ?: continue
                val localPath = obj.optString("localFilePath")
                add(
                    ConvertedItem(
                        id = obj.optString("id"),
                        fileName = obj.optString("fileName"),
                        fileUrl = obj.optString("fileUrl"),
                        createdAt = obj.optLong("createdAt"),
                        imagePath = obj.optString("imagePath"),
                        localFilePath = localPath.ifBlank { null },
                    ),
                )
            }
        }
        return items.sortedByDescending { it.createdAt }
    }

    fun save(
        image: Bitmap?,
        fileName: String,
        fileUrl: String,
    ): ConvertedItem {
        val id = UUID.randomUUID().toString()
        val imagePath = if (image != null) {
            val imageFile = File(imagesDir, "$id.jpg")
            FileOutputStream(imageFile).use { out ->
                image.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            imageFile.absolutePath
        } else {
            ""
        }

        val item = ConvertedItem(
            id = id,
            fileName = fileName,
            fileUrl = fileUrl,
            createdAt = System.currentTimeMillis(),
            imagePath = imagePath,
        )

        val arr = readItemsArray()
        arr.put(
            JSONObject().apply {
                put("id", item.id)
                put("fileName", item.fileName)
                put("fileUrl", item.fileUrl)
                put("createdAt", item.createdAt)
                put("imagePath", item.imagePath)
                put("localFilePath", "")
            },
        )
        writeItemsArray(arr)
        return item
    }

    fun updateLocalFilePath(id: String, path: String) {
        val arr = readItemsArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) {
                obj.put("localFilePath", path)
                break
            }
        }
        writeItemsArray(arr)
    }

    fun rename(item: ConvertedItem, name: String) {
        val arr = readItemsArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") == item.id) {
                obj.put("fileName", name)
                val oldLocalPath = obj.optString("localFilePath").ifBlank { null }
                if (oldLocalPath != null) {
                    val oldFile = File(oldLocalPath)
                    if (oldFile.exists()) {
                        val newFile = File(convertedDir, "$name.docx")
                        if (oldFile.renameTo(newFile)) {
                            obj.put("localFilePath", newFile.absolutePath)
                        }
                    }
                }
                break
            }
        }
        writeItemsArray(arr)
    }

    fun delete(id: String) {
        val arr = readItemsArray()
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) {
                File(obj.optString("imagePath")).delete()
                val localPath = obj.optString("localFilePath").ifBlank { null }
                if (localPath != null) File(localPath).delete()
                continue
            }
            next.put(obj)
        }
        writeItemsArray(next)
    }

    fun doesNameExist(name: String): Boolean {
        return list().any { it.fileName.equals(name, ignoreCase = true) }
    }

    private fun readItemsArray(): JSONArray {
        if (!itemsFile.exists()) return JSONArray()
        return runCatching { JSONArray(itemsFile.readText()) }.getOrDefault(JSONArray())
    }

    private fun writeItemsArray(array: JSONArray) {
        itemsFile.writeText(array.toString())
    }
}
