package com.example.violationrecorder.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.violationrecorder.data.ViolationRecord
import com.example.violationrecorder.databinding.ActivityReportBinding
import com.example.violationrecorder.util.DateUtil
import java.util.Calendar

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private val viewModel: ViolationViewModel by viewModels()

    private var startDate: String = DateUtil.today()
    private var endDate: String = DateUtil.today()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupDatePickers()
        observeReport()
    }

    private fun setupDatePickers() {
        binding.btnStartDate.setOnClickListener {
            showDatePicker(true)
        }
        binding.btnEndDate.setOnClickListener {
            showDatePicker(false)
        }
        updateDateLabels()
    }

    private fun showDatePicker(isStart: Boolean) {
        val calendar = Calendar.getInstance()
        val dateStr = if (isStart) startDate else endDate
        try {
            val parts = dateStr.split("-")
            calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } catch (_: Exception) {}

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = "%04d-%02d-%02d".format(year, month + 1, day)
                if (isStart) startDate = selected else endDate = selected
                updateDateLabels()
                observeReport()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateLabels() {
        binding.tvStartDate.text = DateUtil.formatDisplayDate(startDate)
        binding.tvEndDate.text = DateUtil.formatDisplayDate(endDate)
    }

    private fun observeReport() {
        viewModel.getRecordsByDateRange(startDate, endDate).observe(this) { records ->
            generateReport(records)
        }
    }

    private fun generateReport(records: List<ViolationRecord>?) {
        if (records.isNullOrEmpty()) {
            binding.tvReportContent.text = "此日期區間內沒有任何違規記錄。"
            binding.tvSummary.text = "總計：0 筆"
            return
        }

        // 統計摘要
        val total = records.size
        val typeCount = mutableMapOf<String, Int>()
        val locationCount = mutableMapOf<String, Int>()
        records.forEach { record ->
            typeCount[record.violationType] = typeCount.getOrDefault(record.violationType, 0) + 1
            val locKey = record.address.ifBlank { "未知位置" }
            locationCount[locKey] = locationCount.getOrDefault(locKey, 0) + 1
        }

        // 摘要
        val summary = buildString {
            append("統計期間：${DateUtil.formatDisplayDate(startDate)} ~ ${DateUtil.formatDisplayDate(endDate)}\n")
            append("總計：$total 筆違規記錄\n\n")
            append("【依違規樣態統計】\n")
            typeCount.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                append("・$type：$count 筆\n")
            }
            append("\n【依地點統計】\n")
            locationCount.entries.sortedByDescending { it.value }.take(10).forEach { (loc, count) ->
                append("・$loc：$count 筆\n")
            }
        }
        binding.tvSummary.text = summary

        // 明細
        val detail = buildString {
            append("===== 違規記錄明細 =====\n\n")
            records.sortedBy { it.timestamp }.forEachIndexed { index, record ->
                append("【第 ${index + 1} 筆】\n")
                append("日期時間：${record.date} ${record.time}\n")
                append("違規樣態：${record.violationType}\n")
                append("地點：${record.address}\n")
                append("座標：%.6f, %.6f\n".format(record.latitude, record.longitude))
                append("\n")
            }
        }
        binding.tvReportContent.text = detail
        binding.scrollView.visibility = View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
