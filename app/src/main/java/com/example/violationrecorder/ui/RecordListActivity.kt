package com.example.violationrecorder.ui

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.violationrecorder.data.ViolationRecord
import com.example.violationrecorder.databinding.ActivityRecordListBinding
import com.example.violationrecorder.util.DateUtil
import com.example.violationrecorder.util.ExcelExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class RecordListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecordListBinding
    private val viewModel: ViolationViewModel by viewModels()
    private lateinit var adapter: ViolationRecordAdapter
    private var selectedDate: String = DateUtil.today()
    private var currentRecords: List<ViolationRecord> = emptyList()

    // SAF：建立檔案（讓使用者選擇儲存位置）
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            exportRecordsToExcel(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setupRecyclerView()
        setupDatePicker()
        setupExportButton()
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

    private fun setupExportButton() {
        binding.btnExport.setOnClickListener {
            if (currentRecords.isEmpty()) {
                Toast.makeText(this, "此日期沒有記錄可匯出", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 開啟 SAF 檔案建立器，預設檔名為「違規記錄_日期.xlsx」
            createDocumentLauncher.launch(ExcelExporter.suggestFileName(selectedDate))
        }
    }

    private fun exportRecordsToExcel(uri: Uri) {
        binding.btnExport.isEnabled = false
        Toast.makeText(this, "正在匯出...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        ExcelExporter.exportToExcel(
                            outputStream = outputStream,
                            records = currentRecords,
                            sheetName = "違規記錄_$selectedDate"
                        )
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            binding.btnExport.isEnabled = true
            if (success) {
                Toast.makeText(
                    this@RecordListActivity,
                    "已匯出 ${currentRecords.size} 筆記錄",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@RecordListActivity,
                    "匯出失敗，請重試",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
            currentRecords = records ?: emptyList()
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
