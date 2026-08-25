package com.example.violationrecorder.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.violationrecorder.databinding.ActivityRecordListBinding
import com.example.violationrecorder.util.DateUtil
import java.util.Calendar

class RecordListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordListBinding
    private val viewModel: ViolationViewModel by viewModels()
    private lateinit var adapter: ViolationRecordAdapter

    private var selectedDate: String = DateUtil.today()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupDatePicker()
        observeRecords()
    }

    private fun setupRecyclerView() {
        adapter = ViolationRecordAdapter(
            onDeleteClick = { record ->
                viewModel.delete(record)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupDatePicker() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }
        binding.tvSelectedDate.text = DateUtil.formatDisplayDate(selectedDate)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        // 從 selectedDate 解析
        try {
            val parts = selectedDate.split("-")
            calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } catch (_: Exception) {}

        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = "%04d-%02d-%02d".format(year, month + 1, day)
                binding.tvSelectedDate.text = DateUtil.formatDisplayDate(selectedDate)
                observeRecords()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun observeRecords() {
        viewModel.getRecordsByDate(selectedDate).observe(this) { records ->
            if (records.isNullOrEmpty()) {
                binding.recyclerView.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvCount.text = "共 0 筆記錄"
            } else {
                binding.recyclerView.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                binding.tvCount.text = "共 ${records.size} 筆記錄"
                adapter.submitList(records)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
