package com.csvapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.csvapp.R
import com.csvapp.data.RecordEntity

/**
 * RecyclerView adapter for the search results list.
 *
 * Uses ListAdapter + DiffUtil for efficient list updates —
 * only changed items are redrawn, keeping scroll smooth.
 */
class RecordAdapter(
    private val col1Header: String,
    private val col2Header: String,
    private val col3Header: String,
    private val onItemClick: (RecordEntity) -> Unit
) : ListAdapter<RecordEntity, RecordAdapter.RecordViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecordEntity>() {
            override fun areItemsTheSame(old: RecordEntity, new: RecordEntity) =
                old.id == new.id

            override fun areContentsTheSame(old: RecordEntity, new: RecordEntity) =
                old == new
        }
    }

    // ─── ViewHolder ────────────────────────────────────────────────────────────

    inner class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRowNum: TextView  = itemView.findViewById(R.id.tvRowNum)
        private val tvLabel1: TextView  = itemView.findViewById(R.id.tvLabel1)
        private val tvValue1: TextView  = itemView.findViewById(R.id.tvValue1)
        private val tvLabel2: TextView  = itemView.findViewById(R.id.tvLabel2)
        private val tvValue2: TextView  = itemView.findViewById(R.id.tvValue2)
        private val tvLabel3: TextView  = itemView.findViewById(R.id.tvLabel3)
        private val tvValue3: TextView  = itemView.findViewById(R.id.tvValue3)

        fun bind(record: RecordEntity) {
            tvRowNum.text = "#${record.rowIndex + 1}"

            tvLabel1.text = col1Header
            tvValue1.text = record.col1.ifBlank { "—" }

            tvLabel2.text = col2Header
            tvValue2.text = record.col2.ifBlank { "—" }

            // Show col3 only if it has data
            if (record.col3.isNotBlank() && col3Header.isNotBlank()) {
                tvLabel3.visibility = View.VISIBLE
                tvValue3.visibility = View.VISIBLE
                tvLabel3.text = col3Header
                tvValue3.text = record.col3
            } else {
                tvLabel3.visibility = View.GONE
                tvValue3.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(record) }
        }
    }

    // ─── Adapter overrides ─────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
