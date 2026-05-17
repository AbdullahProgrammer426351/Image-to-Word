package com.image.word.converter.convert.docx.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.image.word.converter.convert.docx.MainApplication
import com.image.word.converter.convert.docx.data.local.db.ConvertedItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as MainApplication).database.convertedDao()

    val savedItems: StateFlow<List<ConvertedItem>> = dao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteItem(item: ConvertedItem) {
        viewModelScope.launch {
            dao.deleteItem(item)
        }
    }
}
