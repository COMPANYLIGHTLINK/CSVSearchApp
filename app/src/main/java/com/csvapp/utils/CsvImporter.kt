package com.csvapp.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.csvapp.data.AppDatabase
import com.csvapp.data.RecordEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Handles reading a CSV file and importing all records into Room in batches.
 *
 * CSV sources supported (checked in this order):
 *  1. User picks file via file picker (URI)
 *  2. External storage: Android/data/com.csvapp/files/data.csv  ← drop file here!
 *  3. Bundled in app assets: assets/data.csv
 *
 * Performance:
 *  - BufferedReader with 64 KB buffer for fast I/O
 *  - Batch inserts of BATCH_SIZE rows at a time
 *  - Runs entirely on IO dispatcher
 */
class CsvImporter(private val context: Context) {

    companion object {
        private const val BATCH_SIZE = 500
        private const val PREFS_NAME = "csv_app_prefs"
        private const val KEY_HEADERS = "csv_headers"
        private const val KEY_DB_POPULATED = "db_populated"
        private const val KEY_RECORD_COUNT = "record_count"
        private const val KEY_COL1_HEADER = "col1_header"
        private const val KEY_COL2_HEADER = "col2_header"
        private const val KEY_COL3_HEADER = "col3_header"

        // File name to look for in external app storage
        const val EXTERNAL_CSV_FILENAME = "data.csv"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(context)
    private val dao = db.recordDao()
    private val gson = Gson()

    // ─── State Checks ──────────────────────────────────────────────────────────

    fun isDbPopulated(): Boolean = prefs.getBoolean(KEY_DB_POPULATED, false)

    fun getStoredHeaders(): List<String> {
        val json = prefs.getString(KEY_HEADERS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCol1Header(): String = prefs.getString(KEY_COL1_HEADER, "Column 1") ?: "Column 1"
    fun getCol2Header(): String = prefs.getString(KEY_COL2_HEADER, "Column 2") ?: "Column 2"
    fun getCol3Header(): String = prefs.getString(KEY_COL3_HEADER, "Column 3") ?: "Column 3"
    fun getRecordCount(): Int = prefs.getInt(KEY_RECORD_COUNT, 0)

    // ─── External Storage Path ─────────────────────────────────────────────────

    /**
     * Returns the path where users can drop their CSV file:
     *   Android/data/com.csvapp/files/data.csv
     *
     * This is app-specific external storage — no special permissions needed.
     * Users can access it via any file manager app (e.g., Files by Google,
     * Solid Explorer, ES File Explorer).
     */
    fun getExternalCsvFile(): File? {
        val externalDir = context.getExternalFilesDir(null) ?: return null
        return File(externalDir, EXTERNAL_CSV_FILENAME)
    }

    fun getExternalCsvPath(): String {
        val file = getExternalCsvFile()
        return file?.absolutePath ?: "Android/data/com.csvapp/files/data.csv"
    }

    fun hasExternalCsv(): Boolean = getExternalCsvFile()?.exists() == true

    // ─── Import from URI (file picker) ────────────────────────────────────────

    suspend fun importFromUri(
        uri: Uri,
        onProgress: (imported: Int, total: Int) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Cannot open file")
            inputStream.use { stream ->
                doImport(BufferedReader(InputStreamReader(stream), 65536), onProgress)
            }
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.localizedMessage}")
        }
    }

    // ─── Import from External Storage (Android/data/com.csvapp/files/) ────────

    /**
     * Imports data.csv placed by the user in the app's external storage folder.
     * Path: Android/data/com.csvapp/files/data.csv
     *
     * Users can copy this file using any Android file manager.
     * No runtime permission needed (app-private external dir).
     */
    suspend fun importFromExternalStorage(
        onProgress: (imported: Int, total: Int) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        val file = getExternalCsvFile()
            ?: return@withContext ImportResult.Error("External storage not available")
        if (!file.exists()) {
            return@withContext ImportResult.Error(
                "File not found: ${file.absolutePath}\n\nCopy your data.csv to this path using a file manager."
            )
        }
        try {
            file.inputStream().use { stream ->
                doImport(BufferedReader(InputStreamReader(stream), 65536), onProgress)
            }
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.localizedMessage}")
        }
    }

    // ─── Import from Assets (bundled) ─────────────────────────────────────────

    suspend fun importFromAssets(
        filename: String = "data.csv",
        onProgress: (imported: Int, total: Int) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            context.assets.open(filename).use { stream ->
                doImport(BufferedReader(InputStreamReader(stream), 65536), onProgress)
            }
        } catch (e: Exception) {
            ImportResult.Error("Cannot read assets/$filename: ${e.localizedMessage}")
        }
    }

    // ─── Core Import Logic ─────────────────────────────────────────────────────

    private suspend fun doImport(
        reader: BufferedReader,
        onProgress: (Int, Int) -> Unit
    ): ImportResult {
        dao.deleteAll()

        val headerLine = reader.readLine()
            ?: return ImportResult.Error("CSV file is empty")

        val headers = parseCsvLine(headerLine)
        if (headers.isEmpty()) return ImportResult.Error("CSV has no headers")

        saveHeaders(headers)

        val batch = mutableListOf<RecordEntity>()
        var rowIndex = 0
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val values = parseCsvLine(line!!)
            if (values.isEmpty()) continue

            batch.add(buildEntity(headers, values, rowIndex))
            rowIndex++

            if (batch.size >= BATCH_SIZE) {
                dao.insertAll(batch)
                batch.clear()
                onProgress(rowIndex, -1)
            }
        }

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            onProgress(rowIndex, rowIndex)
        }

        prefs.edit()
            .putBoolean(KEY_DB_POPULATED, true)
            .putInt(KEY_RECORD_COUNT, rowIndex)
            .apply()

        return ImportResult.Success(rowIndex)
    }

    // ─── CSV Parsing ──────────────────────────────────────────────────────────

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    fields.add(sb.toString().trim()); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString().trim())
        return fields
    }

    // ─── Entity Builder ────────────────────────────────────────────────────────

    private fun buildEntity(
        headers: List<String>,
        values: List<String>,
        rowIndex: Int
    ): RecordEntity {
        fun get(idx: Int) = values.getOrElse(idx) { "" }

        val dataMap = LinkedHashMap<String, String>()
        for (i in headers.indices) dataMap[headers[i]] = get(i)

        return RecordEntity(
            col1     = get(0),
            col2     = get(1),
            col3     = get(2),
            col4     = get(3),
            col5     = get(4),
            fullData = gson.toJson(dataMap),
            rowIndex = rowIndex
        )
    }

    // ─── Header Storage ────────────────────────────────────────────────────────

    private fun saveHeaders(headers: List<String>) {
        prefs.edit()
            .putString(KEY_HEADERS, gson.toJson(headers))
            .putString(KEY_COL1_HEADER, headers.getOrElse(0) { "Column 1" })
            .putString(KEY_COL2_HEADER, headers.getOrElse(1) { "Column 2" })
            .putString(KEY_COL3_HEADER, headers.getOrElse(2) { "Column 3" })
            .apply()
    }

    fun clearData() {
        prefs.edit()
            .remove(KEY_DB_POPULATED)
            .remove(KEY_HEADERS)
            .remove(KEY_RECORD_COUNT)
            .remove(KEY_COL1_HEADER)
            .remove(KEY_COL2_HEADER)
            .remove(KEY_COL3_HEADER)
            .apply()
    }
}

sealed class ImportResult {
    data class Success(val rowsImported: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
