package com.image.word.converter.convert.docx.ui.util

import android.content.Context
import android.widget.Toast
import com.image.word.converter.convert.docx.R
import com.image.word.converter.convert.docx.model.ConvertedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun shareSavedItem(
    context: Context,
    item: ConvertedItem,
    scope: CoroutineScope,
) {
    scope.launch(Dispatchers.Main) {
        val success = WordFileHelper.shareWord(context, item)
        if (!success) {
            Toast.makeText(context, context.getString(R.string.word_share_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
