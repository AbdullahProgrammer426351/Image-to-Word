package com.image.word.converter.convert.docx.ui.state

import android.net.Uri
import com.image.word.converter.convert.docx.model.ConvertedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionUiState(
    val selectedImageUris: List<Uri> = emptyList(),
    val convertedItems: List<ConvertedItem> = emptyList(),
)

class SessionState {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    fun setSelectedImages(uris: List<Uri>) {
        _uiState.update { it.copy(selectedImageUris = uris) }
    }

    fun addImages(uris: List<Uri>) {
        _uiState.update {
            it.copy(selectedImageUris = it.selectedImageUris + uris)
        }
    }

    fun clearSelectedImages() {
        _uiState.update { it.copy(selectedImageUris = emptyList()) }
    }

    fun updateImage(index: Int, uri: Uri) {
        _uiState.update {
            val updated = it.selectedImageUris.toMutableList()
            if (index in updated.indices) {
                updated[index] = uri
            }
            it.copy(selectedImageUris = updated)
        }
    }

    fun removeImage(index: Int) {
        _uiState.update {
            val updated = it.selectedImageUris.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            it.copy(selectedImageUris = updated)
        }
    }

    fun moveImage(from: Int, to: Int) {
        _uiState.update {
            val updated = it.selectedImageUris.toMutableList()
            if (from in updated.indices && to in updated.indices && from != to) {
                val item = updated.removeAt(from)
                updated.add(to, item)
            }
            it.copy(selectedImageUris = updated)
        }
    }

    fun setConvertedItems(items: List<ConvertedItem>) {
        _uiState.update { it.copy(convertedItems = items) }
    }

    fun clearConvertedItems() {
        _uiState.update { it.copy(convertedItems = emptyList()) }
    }
}
