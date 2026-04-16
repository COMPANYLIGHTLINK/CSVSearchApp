package com.csvapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single record imported from the CSV file.
 *
 * Design decisions:
 * - col1..col5 store the first 5 columns of the CSV for fast indexed searching
 * - fullData stores ALL columns as a JSON string for the detail view
 * - Indexes on col1, col2, col3 enable fast LIKE queries
 * - FTS5 virtual table (created manually in AppDatabase) enables full-text search
 */
@Entity(
    tableName = "records",
    indices = [
        Index(value = ["col1"]),
        Index(value = ["col2"]),
        Index(value = ["col3"]),
        Index(value = ["row_index"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // First 5 columns stored individually for searching and list display
    @ColumnInfo(name = "col1") val col1: String = "",
    @ColumnInfo(name = "col2") val col2: String = "",
    @ColumnInfo(name = "col3") val col3: String = "",
    @ColumnInfo(name = "col4") val col4: String = "",
    @ColumnInfo(name = "col5") val col5: String = "",

    // Full row data as JSON map {"header": "value", ...}
    @ColumnInfo(name = "full_data") val fullData: String = "{}",

    // Original row number in the CSV (for reference)
    @ColumnInfo(name = "row_index") val rowIndex: Int = 0
)
