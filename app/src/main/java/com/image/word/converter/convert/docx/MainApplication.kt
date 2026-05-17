package com.image.word.converter.convert.docx

import android.app.Application
import com.image.word.converter.convert.docx.data.local.db.AppDatabase

class MainApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
