package com.image.word.converter.convert.docx.ui.state

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.image.word.converter.convert.docx.data.ConvertedRepository
import com.image.word.converter.convert.docx.model.ConvertedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val savedItems: List<ConvertedItem> = emptyList(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConvertedRepository(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshSavedItems()
    }

    fun refreshSavedItems() {
        _uiState.update { it.copy(savedItems = repository.list()) }
    }

    fun saveConverted(
        image: Bitmap?,
        fileName: String,
        fileUrl: String,
        onSaved: (ConvertedItem) -> Unit = {},
    ) {
        viewModelScope.launch {
            val item = saveConvertedNow(image, fileName, fileUrl)
            refreshSavedItems()
            onSaved(item)
        }
    }

    fun saveConvertedNow(
        image: Bitmap?,
        fileName: String,
        fileUrl: String,
    ): ConvertedItem {
        return repository.save(image = image, fileName = fileName, fileUrl = fileUrl).also {
            refreshSavedItems()
        }
    }

    fun rename(item: ConvertedItem, newName: String) {
        repository.rename(item.id, newName)
        refreshSavedItems()
    }

    fun delete(item: ConvertedItem) {
        repository.delete(item.id)
        refreshSavedItems()
    }

    fun doesNameExist(name: String): Boolean = repository.doesNameExist(name)

    fun readBitmap(item: ConvertedItem): Bitmap? {
        if (item.imagePath.isBlank()) return null
        return com.image.word.converter.convert.docx.util.decodeBitmapFromFile(item.imagePath)
    }
}
