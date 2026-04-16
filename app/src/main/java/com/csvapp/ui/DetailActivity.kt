package com.csvapp.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.csvapp.CSVApp
import com.csvapp.R
import com.csvapp.databinding.ActivityDetailBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detail view — displays all fields of a single record.
 *
 * Receives the record ID via Intent, fetches it from the DB,
 * then dynamically creates label/value rows for every column.
 */
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECORD_ID = "extra_record_id"
    }

    private lateinit var binding: ActivityDetailBinding
    private val gson = Gson()
    private val app by lazy { application as CSVApp }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Record Detail"

        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        if (recordId == -1L) {
            showError("Invalid record ID")
            return
        }

        loadRecord(recordId)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ─── Data Loading ──────────────────────────────────────────────────────────

    private fun loadRecord(id: Long) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val record = withContext(Dispatchers.IO) {
                app.repository.getById(id)
            }

            binding.progressBar.visibility = View.GONE

            if (record == null) {
                showError("Record not found")
                return@launch
            }

            // Parse fullData JSON → LinkedHashMap (preserves column order)
            val type = object : TypeToken<LinkedHashMap<String, String>>() {}.type
            val dataMap: LinkedHashMap<String, String> = try {
                gson.fromJson(record.fullData, type) ?: LinkedHashMap()
            } catch (e: Exception) {
                LinkedHashMap()
            }

            // Show row number as subtitle
            binding.tvRecordTitle.text = "Row #${record.rowIndex + 1}"
            binding.tvRecordSubtitle.text = "ID: ${record.id}"

            // Dynamically add a row for each field
            populateFields(dataMap)
        }
    }

    // ─── UI ────────────────────────────────────────────────────────────────────

    private fun populateFields(data: LinkedHashMap<String, String>) {
        val container = binding.fieldsContainer
        container.removeAllViews()

        if (data.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No data available"
            container.addView(tv)
            return
        }

        data.forEach { (key, value) ->
            val fieldView = layoutInflater.inflate(R.layout.item_detail_field, container, false)
            fieldView.findViewById<TextView>(R.id.tvFieldLabel).text = key
            fieldView.findViewById<TextView>(R.id.tvFieldValue).text = value.ifBlank { "—" }
            container.addView(fieldView)
        }
    }

    private fun showError(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }
}
