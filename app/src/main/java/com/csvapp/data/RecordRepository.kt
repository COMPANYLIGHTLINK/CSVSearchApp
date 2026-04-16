package com.csvapp.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository layer — single source of truth for record data.
 *
 * Handles the logic of choosing between FTS5 search and LIKE fallback,
 * and manages pagination for the search results.
 */
class RecordRepository(private val dao: RecordDao) {

    companion object {
        const val PAGE_SIZE = 30
    }

    // ─── Count ─────────────────────────────────────────────────────────────────

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        dao.getCount()
    }

    // ─── Paged full list ───────────────────────────────────────────────────────

    suspend fun getPage(page: Int): List<RecordEntity> = withContext(Dispatchers.IO) {
        dao.getPage(PAGE_SIZE, page * PAGE_SIZE)
    }

    // ─── Search ────────────────────────────────────────────────────────────────

    /**
     * Performs a search using FTS5 (preferred) or LIKE (fallback).
     *
     * FTS5 query syntax:
     *  - "word*" matches prefix
     *  - "\"exact phrase\"" matches exact
     *  - "word1 OR word2" matches either
     *
     * @param rawQuery  user-typed text
     * @param page      zero-based page number
     * @return          list of matching RecordEntity items
     */
    suspend fun search(rawQuery: String, page: Int): SearchResult = withContext(Dispatchers.IO) {
        if (rawQuery.isBlank()) {
            val items = dao.getPage(PAGE_SIZE, page * PAGE_SIZE)
            val total = dao.getCount()
            return@withContext SearchResult(items, total)
        }

        // Try FTS5 first
        return@withContext try {
            searchWithFts(rawQuery.trim(), page)
        } catch (e: Exception) {
            // FTS may fail on special characters — fall back to LIKE
            searchWithLike(rawQuery.trim(), page)
        }
    }

    // ─── FTS5 ──────────────────────────────────────────────────────────────────

    private suspend fun searchWithFts(query: String, page: Int): SearchResult {
        val ftsQuery = buildFtsQuery(query)
        val offset = page * PAGE_SIZE

        val sql = """
            SELECT r.* FROM records r
            INNER JOIN records_fts fts ON r.id = fts.rowid
            WHERE records_fts MATCH ?
            ORDER BY rank
            LIMIT $PAGE_SIZE OFFSET $offset
        """.trimIndent()

        val countSql = """
            SELECT COUNT(*) FROM records r
            INNER JOIN records_fts fts ON r.id = fts.rowid
            WHERE records_fts MATCH ?
        """.trimIndent()

        val items = dao.searchFts(SimpleSQLiteQuery(sql, arrayOf(ftsQuery)))
        val total = dao.countFts(SimpleSQLiteQuery(countSql, arrayOf(ftsQuery)))
        return SearchResult(items, total)
    }

    /**
     * Converts a raw user query into a valid FTS5 match expression.
     * Escapes special FTS5 characters and appends prefix wildcard.
     */
    private fun buildFtsQuery(query: String): String {
        // Split into tokens, escape quotes, append * for prefix matching
        return query.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                // Escape double quotes within token
                val escaped = token.replace("\"", "\"\"")
                "\"$escaped\"*"
            }
    }

    // ─── LIKE Fallback ─────────────────────────────────────────────────────────

    private suspend fun searchWithLike(query: String, page: Int): SearchResult {
        val likeQuery = "%$query%"
        val exactQuery = "$query%"
        val items = dao.searchLike(likeQuery, exactQuery, PAGE_SIZE, page * PAGE_SIZE)
        val total = dao.countLike(likeQuery)
        return SearchResult(items, total)
    }

    // ─── Single Record ─────────────────────────────────────────────────────────

    suspend fun getById(id: Long): RecordEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    // ─── Clear Database ────────────────────────────────────────────────────────

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        // Also clear FTS index
        dao.runRaw(SimpleSQLiteQuery("DELETE FROM records_fts"))
    }
}

data class SearchResult(
    val items: List<RecordEntity>,
    val totalCount: Int
)
