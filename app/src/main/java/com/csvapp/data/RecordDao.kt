package com.csvapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Data Access Object for the records table.
 *
 * Uses both standard Room queries (for simple lookups) and raw queries
 * (for FTS5 full-text search performance).
 */
@Dao
interface RecordDao {

    // ─── Insert ────────────────────────────────────────────────────────────────

    /**
     * Batch-insert records for efficient CSV import.
     * Inserting in batches of ~500-1000 rows is much faster than one-by-one.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RecordEntity>)

    // ─── Count ─────────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM records")
    suspend fun getCount(): Int

    // ─── Single Record Lookup ──────────────────────────────────────────────────

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecordEntity?

    // ─── Paginated Full List (no filter) ──────────────────────────────────────

    @Query("SELECT * FROM records ORDER BY row_index ASC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<RecordEntity>

    // ─── LIKE Search (fallback if FTS unavailable) ────────────────────────────

    /**
     * Searches col1, col2, col3 using LIKE with indexed columns.
     * Wrapped query (e.g., %query%) — slower than FTS but simpler.
     */
    @Query("""
        SELECT * FROM records
        WHERE col1 LIKE :query OR col2 LIKE :query OR col3 LIKE :query
           OR col4 LIKE :query OR col5 LIKE :query
        ORDER BY
            CASE WHEN col1 LIKE :exactQuery THEN 0
                 WHEN col2 LIKE :exactQuery THEN 1
                 ELSE 2 END,
            row_index ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchLike(
        query: String,       // e.g., "%keyword%"
        exactQuery: String,  // e.g., "keyword%"
        limit: Int,
        offset: Int
    ): List<RecordEntity>

    @Query("""
        SELECT COUNT(*) FROM records
        WHERE col1 LIKE :query OR col2 LIKE :query OR col3 LIKE :query
           OR col4 LIKE :query OR col5 LIKE :query
    """)
    suspend fun countLike(query: String): Int

    // ─── FTS5 Search (fast, preferred) ────────────────────────────────────────

    /**
     * Full-text search via FTS5 virtual table JOIN.
     * FTS5 uses inverted index — orders of magnitude faster than LIKE on 100k rows.
     */
    @RawQuery
    suspend fun searchFts(query: SupportSQLiteQuery): List<RecordEntity>

    @RawQuery
    suspend fun countFts(query: SupportSQLiteQuery): Int

    // ─── Utility ───────────────────────────────────────────────────────────────

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    @RawQuery
    suspend fun runRaw(query: SupportSQLiteQuery): Int
}
