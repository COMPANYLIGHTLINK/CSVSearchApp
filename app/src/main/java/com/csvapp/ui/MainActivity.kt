package com.csvapp.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.csvapp.CSVApp
import com.csvapp.R
import com.csvapp.databinding.ActivityMainBinding
import com.csvapp.data.RecordEntity

/**
 * Main screen — search bar + RecyclerView results.
 *
 * Flow:
 *  1. On launch, check if DB is populated → redirect to ImportActivity if not
 *  2. Search bar drives ViewModel query with debouncing
 *  3. RecyclerView infinite-scrolls via loadNextPage()
 *  4. Click item → open DetailActivity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app by lazy { application as CSVApp }

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(app.repository)
    }

    private lateinit var adapter: RecordAdapter

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        // Redirect to import if DB is empty
        if (!app.csvImporter.isDbPopulated()) {
            startImportActivity()
            return
        }

        setupAdapter()
        setupRecyclerView()
        setupSearchBar()
        observeViewModel()

        // Initial load of all records
        viewModel.onQueryChanged("")
    }

    override fun onResume() {
        super.onResume()
        // Refresh if returning from import
        if (app.csvImporter.isDbPopulated()) {
            viewModel.refresh()
        }
    }

    // ─── Menu ──────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import -> {
                confirmReimport()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─── Setup ─────────────────────────────────────────────────────────────────

    private fun setupAdapter() {
        adapter = RecordAdapter(
            col1Header = app.csvImporter.getCol1Header(),
            col2Header = app.csvImporter.getCol2Header(),
            col3Header = app.csvImporter.getCol3Header(),
            onItemClick = ::openDetail
        )
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)

        // Infinite scroll — trigger loadNextPage when near bottom
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return // only load when scrolling down
                val visible = layoutManager.childCount
                val total = layoutManager.itemCount
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (firstVisible + visible >= total - 5) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onQueryChanged(s?.toString() ?: "")
            }
        })

        // Clear button
        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    // ─── Observe ───────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.records.observe(this) { records ->
            adapter.submitList(records)
            updateEmptyState(records.isEmpty())
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.isLoadingMore.observe(this) { loading ->
            binding.progressBarBottom.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.totalCount.observe(this) { count ->
            val query = binding.etSearch.text?.toString() ?: ""
            binding.tvResultCount.text = if (query.isBlank()) {
                "${app.csvImporter.getRecordCount()} records total"
            } else {
                "$count result${if (count != 1) "s" else ""} found"
            }
        }

        viewModel.errorMessage.observe(this) { msg ->
            if (msg != null) {
                binding.tvError.text = msg
                binding.tvError.visibility = View.VISIBLE
            } else {
                binding.tvError.visibility = View.GONE
            }
        }
    }

    // ─── Navigation ────────────────────────────────────────────────────────────

    private fun openDetail(record: RecordEntity) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_RECORD_ID, record.id)
        }
        startActivity(intent)
    }

    private fun startImportActivity() {
        startActivity(Intent(this, ImportActivity::class.java))
        finish()
    }

    // ─── UI Helpers ────────────────────────────────────────────────────────────

    private fun updateEmptyState(empty: Boolean) {
        val loading = viewModel.isLoading.value == true
        binding.tvEmpty.visibility = if (empty && !loading) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun confirmReimport() {
        AlertDialog.Builder(this)
            .setTitle("Re-import CSV")
            .setMessage("This will clear the existing database and re-import from a new file. Continue?")
            .setPositiveButton("Import") { _, _ ->
                app.csvImporter.clearData()
                startImportActivity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        val count = app.csvImporter.getRecordCount()
        val headers = app.csvImporter.getStoredHeaders()
        AlertDialog.Builder(this)
            .setTitle("About")
            .setMessage(
                "CSV Search App\n\n" +
                "Records in DB: $count\n" +
                "Columns: ${headers.size}\n" +
                "Searchable columns:\n" +
                headers.take(5).joinToString("\n") { "  • $it" }
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
