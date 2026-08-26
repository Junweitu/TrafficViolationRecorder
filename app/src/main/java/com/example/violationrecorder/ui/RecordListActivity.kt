package com.example.violationrecorder.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.violationrecorder.data.ViolationRecord
import com.example.violationrecorder.databinding.ActivityRecordListBinding
import com.example.violationrecorder.util.DateUtil
import com.example.violationrecorder.util.ExcelExporter
import com.example.violationrecorder.util.ExcelImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class RecordListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecordListBinding
    private val viewModel: ViolationViewModel by viewModels()
    private lateinit var adapter: ViolationRecordAdapter
    private var selectedDate: String = DateUtil.today()
    private var currentRecords: List<ViolationRecord> = emptyList()

    // SAF：建立檔案（匯出時讓使用者選擇儲存位置）
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            exportRecordsToExcel(uri)
        }
    }

    // SAF：開啟檔案（匯入時讓使用者選擇 xlsx 檔案）
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importRecordsFromExcel(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setupRecyclerView()
        setupDatePicker()
        setupButtons()
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

    private fun setupButtons() {
        // 匯出 Excel
        binding.btnExport.setOnClickListener {
            if (currentRecords.isEmpty()) {
                Toast.makeText(this, "此日期沒有記錄可匯出", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createDocumentLauncher.launch(ExcelExporter.suggestFileName(selectedDate))
        }

        // 匯入 Excel
        binding.btnImport.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/octet-stream"
                )
            )
        }

        // 分享：將目前日期的記錄匯出為暫存檔後分享
        binding.btnShare.setOnClickListener {
            if (currentRecords.isEmpty()) {
                Toast.makeText(this, "此日期沒有記錄可分享", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            shareRecordsAsExcel()
        }
    }

    /**
     * 匯出記錄到使用者選擇的位置
     */
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
                Toast.makeText(this@RecordListActivity, "匯出失敗，請重試", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 從使用者選擇的 xlsx 檔案匯入記錄
     */
    private fun importRecordsFromExcel(uri: Uri) {
        binding.btnImport.isEnabled = false
        Toast.makeText(this, "正在匯入...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val records = ExcelImporter.importFromExcel(inputStream)
                        if (records.isNotEmpty()) {
                            viewModel.insertAll(records)
                        }
                        Result.success(records.size)
                    } ?: Result.failure(Exception("無法開啟檔案"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Result.failure(e)
                }
            }

            binding.btnImport.isEnabled = true
            result.onSuccess { count ->
                if (count > 0) {
                    Toast.makeText(
                        this@RecordListActivity,
                        "成功匯入 $count 筆記錄",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@RecordListActivity,
                        "檔案中沒有可匯入的記錄",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure {
                Toast.makeText(
                    this@RecordListActivity,
                    "匯入失敗：${it.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 分享：將記錄寫入快取檔案，透過 FileProvider 分享
     */
    private fun shareRecordsAsExcel() {
        binding.btnShare.isEnabled = false
        Toast.makeText(this, "正在準備分享...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            val fileUri = withContext(Dispatchers.IO) {
                try {
                    val cacheDir = cacheDir
                    val fileName = ExcelExporter.suggestFileName(selectedDate)
                    val file = File(cacheDir, fileName)
                    file.outputStream().use { outputStream ->
                        ExcelExporter.exportToExcel(
                            outputStream = outputStream,
                            records = currentRecords,
                            sheetName = "違規記錄_$selectedDate"
                        )
                    }
                    FileProvider.getUriForFile(
                        this@RecordListActivity,
                        "${packageName}.fileprovider",
                        file
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            binding.btnShare.isEnabled = true
            if (fileUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, "違規記錄_$selectedDate")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "分享違規記錄"))
            } else {
                Toast.makeText(this@RecordListActivity, "分享失敗，請重試", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
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
