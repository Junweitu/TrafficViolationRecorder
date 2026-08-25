package com.example.violationrecorder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.violationrecorder.data.ViolationRecord
import com.example.violationrecorder.databinding.ItemViolationRecordBinding

class ViolationRecordAdapter(
    private val onDeleteClick: (ViolationRecord) -> Unit
) : ListAdapter<ViolationRecord, ViolationRecordAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemViolationRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: ViolationRecord) {
            binding.tvDateTime.text = "${record.date} ${record.time}"
            binding.tvViolationType.text = record.violationType
            binding.tvAddress.text = record.address
            binding.tvCoordinates.text = "%.6f, %.6f".format(record.latitude, record.longitude)
            binding.btnDelete.setOnClickListener {
                onDeleteClick(record)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemViolationRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<ViolationRecord>() {
        override fun areItemsTheSame(oldItem: ViolationRecord, newItem: ViolationRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ViolationRecord, newItem: ViolationRecord): Boolean {
            return oldItem == newItem
        }
    }
}
