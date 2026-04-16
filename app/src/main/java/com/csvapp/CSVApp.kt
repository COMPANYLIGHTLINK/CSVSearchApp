package com.csvapp

import android.app.Application
import androidx.multidex.MultiDexApplication
import com.csvapp.data.AppDatabase
import com.csvapp.data.RecordRepository
import com.csvapp.utils.CsvImporter

/**
 * Application class — initializes singletons.
 */
class CSVApp : MultiDexApplication() {

    // Lazy-initialized singletons accessible app-wide
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { RecordRepository(database.recordDao()) }
    val csvImporter by lazy { CsvImporter(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
