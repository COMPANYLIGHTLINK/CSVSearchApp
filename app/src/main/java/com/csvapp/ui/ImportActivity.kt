package com.csvapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.csvapp.CSVApp
import com.csvapp.R
import com.csvapp.databinding.ActivityImportBinding
import com.csvapp.utils.ImportResult
import kotlinx.coroutines.launch

/**
 * Import screen — shown on first launch or when re-importing.
 *
 * CSV can come from 3 sources (shown in priority order):
 *  1. External app storage: Android/data/com.csvapp/files/data.csv  ← easiest for users
 *  2. File picker — user browses and selects any CSV file
 *  3. Assets folder — developer-bundled data.csv
 */
class ImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding
    private val app by lazy { application as CSVApp }
    private var isImporting = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) startImportFromUri(uri)
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val externalPath = app.csvImporter.getExternalCsvPath()
        val hasExternal  = app.csvImporter.hasExternalCsv()
        val hasAssets    = hasAssetsCsv()

        when {
            // Auto-start if external CSV already exists
            hasExternal -> {
                binding.tvImportHint.text =
                    "Found data.csv in your device storage!\n\n" +
                    "Path: $externalPath\n\n" +
                    "Tap Import to load it, or pick a different file."
                binding.btnImportExternal.visibility = View.VISIBLE
                binding.btnImportExternal.text = "Import  data.csv  (found!)"
            }
            hasAssets -> {
                binding.tvImportHint.text =
                    "A bundled data.csv was found in app assets.\n\n" +
                    "Or copy your own CSV to:\n$externalPath"
                binding.btnImportExternal.visibility = View.GONE
            }
            else -> {
                binding.tvImportHint.text =
                    "Drop your CSV file here:\n\n" +
                    "📂  $externalPath\n\n" +
                    "Copy it there using any file manager app, then tap Refresh.\n\n" +
                    "— OR —\n\nTap \"Pick File\" to browse and select manually."
                binding.btnImportExternal.visibility = View.GONE
            }
        }

        // Show assets button only if assets CSV exists
        binding.btnImportAssets.visibility = if (hasAssets) View.VISIBLE else View.GONE

        // Button handlers
        binding.btnPickFile.setOnClickListener {
            if (!isImporting) filePickerLauncher.launch("text/*")
        }

        binding.btnImportExternal.setOnClickListener {
            if (!isImporting) startImportFromExternal()
        }

        binding.btnImportAssets.setOnClickListener {
            if (!isImporting) startImportFromAssets()
        }

        // Refresh button — re-check if CSV was dropped in the folder
        binding.btnRefresh.setOnClickListener {
            if (app.csvImporter.hasExternalCsv()) {
                binding.btnImportExternal.visibility = View.VISIBLE
                binding.btnImportExternal.text = "Import  data.csv  (found!)"
                binding.tvImportHint.text = "✓ Found data.csv! Tap Import to load it."
            } else {
                showSnackbar("data.csv not found yet. Copy it to:\n${app.csvImporter.getExternalCsvPath()}")
            }
        }

        // Help button
        binding.btnHelp.setOnClickListener {
            showHelpDialog()
        }
    }

    // ─── Import Handlers ───────────────────────────────────────────────────────

    private fun startImportFromUri(uri: Uri) {
        isImporting = true
        setUiImporting(true, "Reading file…")
        lifecycleScope.launch {
            val result = app.csvImporter.importFromUri(uri) { imported, _ ->
                runOnUiThread { binding.tvProgress.text = "Importing… $imported rows" }
            }
            handleResult(result)
        }
    }

    private fun startImportFromExternal() {
        isImporting = true
        setUiImporting(true, "Reading from device storage…")
        lifecycleScope.launch {
            val result = app.csvImporter.importFromExternalStorage { imported, _ ->
                runOnUiThread { binding.tvProgress.text = "Importing… $imported rows" }
            }
            handleResult(result)
        }
    }

    private fun startImportFromAssets() {
        isImporting = true
        setUiImporting(true, "Reading bundled file…")
        lifecycleScope.launch {
            val result = app.csvImporter.importFromAssets { imported, _ ->
                runOnUiThread { binding.tvProgress.text = "Importing… $imported rows" }
            }
            handleResult(result)
        }
    }

    private fun handleResult(result: ImportResult) {
        isImporting = false
        when (result) {
            is ImportResult.Success -> {
                binding.tvProgress.text = "✓ Imported ${result.rowsImported} records"
                binding.progressBar.progress = 100
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            is ImportResult.Error -> {
                setUiImporting(false, "")
                binding.tvError.text = result.message
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    // ─── UI Helpers ────────────────────────────────────────────────────────────

    private fun setUiImporting(importing: Boolean, progressText: String) {
        binding.btnPickFile.isEnabled       = !importing
        binding.btnImportExternal.isEnabled = !importing
        binding.btnImportAssets.isEnabled   = !importing
        binding.btnRefresh.isEnabled        = !importing
        binding.progressBar.visibility      = if (importing) View.VISIBLE else View.GONE
        binding.tvProgress.visibility       = if (importing) View.VISIBLE else View.GONE
        binding.tvError.visibility          = View.GONE
        if (importing) {
            binding.progressBar.isIndeterminate = true
            binding.tvProgress.text = progressText
        }
    }

    private fun showSnackbar(msg: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("How to import your CSV")
            .setMessage(
                "METHOD 1 — Drop file (easiest to update later):\n\n" +
                "1. Install the app\n" +
                "2. Open any file manager app\n" +
                "3. Navigate to:\n   ${app.csvImporter.getExternalCsvPath()}\n" +
                "4. Copy your data.csv there\n" +
                "5. Come back and tap Refresh\n\n" +
                "To update data later: just replace data.csv and re-import!\n\n" +
                "───────────────────────\n\n" +
                "METHOD 2 — File picker:\n\n" +
                "Tap \"Pick File\" and browse to any CSV on your device.\n\n" +
                "CSV requirements:\n" +
                "• First row = column headers\n" +
                "• Comma-separated\n" +
                "• UTF-8 encoding"
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun hasAssetsCsv(): Boolean {
        return try { assets.open("data.csv").close(); true }
        catch (e: Exception) { false }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isImporting) super.onBackPressed()
    }
}
