package com.csvapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database singleton.
 *
 * Performance notes:
 *  - WAL (Write-Ahead Logging) journal mode is enabled for better concurrency
 *  - FTS5 virtual table and supporting triggers are created in the callback
 *  - Indexes on col1/col2/col3 are declared in RecordEntity
 */
@Database(
    entities = [RecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    companion object {
        const val DATABASE_NAME = "csv_search.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Creates the FTS5 virtual table and synchronization triggers after the
     * database is first opened. FTS5 is an external-content table backed by
     * the `records` table — it does NOT store data redundantly but indexes it.
     */
    private class DatabaseCallback : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            setupFts(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Enable WAL mode for better read concurrency during search
            db.execSQL("PRAGMA journal_mode=WAL")
            db.execSQL("PRAGMA synchronous=NORMAL")
            db.execSQL("PRAGMA cache_size=-8000") // 8 MB page cache
            db.execSQL("PRAGMA temp_store=MEMORY")
        }

        private fun setupFts(db: SupportSQLiteDatabase) {
            // FTS5 external-content table — indexes col1..col5 for fast search
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS records_fts
                USING fts5(
                    col1, col2, col3, col4, col5,
                    content='records',
                    content_rowid='id',
                    tokenize='unicode61 remove_diacritics 1'
                )
            """.trimIndent())

            // Trigger: keep FTS in sync on INSERT
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS records_ai
                AFTER INSERT ON records BEGIN
                    INSERT INTO records_fts(rowid, col1, col2, col3, col4, col5)
                    VALUES (new.id, new.col1, new.col2, new.col3, new.col4, new.col5);
                END
            """.trimIndent())

            // Trigger: keep FTS in sync on DELETE
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS records_ad
                AFTER DELETE ON records BEGIN
                    INSERT INTO records_fts(records_fts, rowid, col1, col2, col3, col4, col5)
                    VALUES ('delete', old.id, old.col1, old.col2, old.col3, old.col4, old.col5);
                END
            """.trimIndent())

            // Trigger: keep FTS in sync on UPDATE
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS records_au
                AFTER UPDATE ON records BEGIN
                    INSERT INTO records_fts(records_fts, rowid, col1, col2, col3, col4, col5)
                    VALUES ('delete', old.id, old.col1, old.col2, old.col3, old.col4, old.col5);
                    INSERT INTO records_fts(rowid, col1, col2, col3, col4, col5)
                    VALUES (new.id, new.col1, new.col2, new.col3, new.col4, new.col5);
                END
            """.trimIndent())
        }
    }
}
