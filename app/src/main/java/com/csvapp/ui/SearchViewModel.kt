package com.csvapp.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csvapp.data.RecordEntity
import com.csvapp.data.RecordRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel for the main search screen.
 *
 * Responsibilities:
 *  - Debounces user keystrokes (300ms) before querying
 *  - Manages pagination state (current page, has more)
 *  - Exposes LiveData for UI observation
 *  - Never touches the UI thread for database work
 */
class SearchViewModel(private val repository: RecordRepository) : ViewModel() {

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val MIN_QUERY_LENGTH = 1
    }

    // ─── Live Data ─────────────────────────────────────────────────────────────

    private val _records = MutableLiveData<List<RecordEntity>>(emptyList())
    val records: LiveData<List<RecordEntity>> = _records

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    private val _totalCount = MutableLiveData(0)
    val totalCount: LiveData<Int> = _totalCount

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    // ─── State ─────────────────────────────────────────────────────────────────

    private var currentQuery: String = ""
    private var currentPage = 0
    private var hasMorePages = true
    private var searchJob: Job? = null

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Called when user types in the search bar.
     * Debounces input to avoid hammering the database on every keystroke.
     */
    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            if (query != currentQuery || currentQuery.isEmpty()) {
                currentQuery = query
                resetAndSearch()
            }
        }
    }

    /**
     * Called when RecyclerView scrolls near the bottom — load next page.
     */
    fun loadNextPage() {
        if (_isLoadingMore.value == true || !hasMorePages) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val result = repository.search(currentQuery, currentPage + 1)
                if (result.items.isNotEmpty()) {
                    currentPage++
                    val combined = (_records.value ?: emptyList()) + result.items
                    _records.value = combined
                    hasMorePages = combined.size < result.totalCount
                } else {
                    hasMorePages = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error loading more: ${e.localizedMessage}"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /** Refresh the current search (e.g., after import). */
    fun refresh() {
        resetAndSearch()
    }

    // ─── Internal ──────────────────────────────────────────────────────────────

    private fun resetAndSearch() {
        currentPage = 0
        hasMorePages = true
        _records.value = emptyList()
        performSearch()
    }

    private fun performSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.search(currentQuery, 0)
                _records.value = result.items
                _totalCount.value = result.totalCount
                hasMorePages = result.items.size < result.totalCount
            } catch (e: Exception) {
                _errorMessage.value = "Search error: ${e.localizedMessage}"
                _records.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
