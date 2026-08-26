package com.example.violationrecorder.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.violationrecorder.data.ViolationRecord
import com.example.violationrecorder.databinding.ActivityMainBinding
import com.example.violationrecorder.util.DateUtil
import com.example.violationrecorder.util.LocationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ViolationViewModel by viewModels()

    private var currentLocation: Location? = null
    private var currentAddress: String = ""

    // 定位權限請求
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) {
            fetchLocation()
        } else {
            Toast.makeText(this, "需要定位權限才能記錄違規地點", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        checkLocationPermissionAndFetch()
    }

    private fun setupClickListeners() {
        // 重新整理位置（主動要求新的 GPS 定位）
        binding.btnRefreshLocation.setOnClickListener {
            checkLocationPermissionAndFetch(forceFresh = true)
        }

        // 記錄違規
        binding.btnRecord.setOnClickListener {
            recordViolation()
        }

        // 查看清單
        binding.btnViewList.setOnClickListener {
            startActivity(Intent(this, RecordListActivity::class.java))
        }

        // 查看報表
        binding.btnViewReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }

    private fun checkLocationPermissionAndFetch(forceFresh: Boolean = false) {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                fetchLocation(forceFresh)
            }
            else -> {
                requestLocationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation(forceFresh: Boolean = false) {
        // 禁用按鈕避免重複點擊
        binding.btnRefreshLocation.isEnabled = false
        binding.tvLocationStatus.text = if (forceFresh) "正在重新定位，請稍候..." else "定位中..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val location = if (forceFresh) {
                    // 主動要求新的 GPS 定位
                    LocationUtil.requestFreshLocation(this@MainActivity)
                } else {
                    // 初始載入：先取快取位置
                    LocationUtil.getLastKnownLocation(this@MainActivity)
                }

                if (location != null) {
                    currentLocation = location
                    val address = LocationUtil.getAddressFromLocation(
                        this@MainActivity,
                        location.latitude,
                        location.longitude
                    )
                    currentAddress = address
                    updateLocationUI(location, address)
                    if (forceFresh) {
                        Toast.makeText(this@MainActivity, "定位已更新", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    binding.tvLocationStatus.text = "無法取得位置，請確認 GPS 已開啟並移動到戶外"
                    Toast.makeText(
                        this@MainActivity,
                        if (forceFresh) "定位逾時，請確認 GPS 已開啟" else "無法取得位置，請點擊重新定位",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                binding.tvLocationStatus.text = "定位失敗：${e.message}"
            } finally {
                binding.btnRefreshLocation.isEnabled = true
            }
        }
    }

    private fun updateLocationUI(location: Location, address: String) {
        binding.tvLocationStatus.text = "目前位置："
        binding.tvAddress.text = address
        binding.tvCoordinates.text = "座標：%.6f, %.6f".format(location.latitude, location.longitude)
    }

    private fun recordViolation() {
        val violationType = binding.etViolationType.text.toString().trim()
        if (violationType.isEmpty()) {
            Toast.makeText(this, "請輸入違規樣態", Toast.LENGTH_SHORT).show()
            binding.etViolationType.requestFocus()
            return
        }

        val location = currentLocation
        if (location == null) {
            // 位置不可用時，仍允許記錄，但標示為未知
            AlertDialog.Builder(this)
                .setTitle("位置資訊不足")
                .setMessage("目前無法取得 GPS 位置，是否仍要記錄？（座標與地址將標示為未知）")
                .setPositiveButton("是") { _, _ ->
                    saveRecord(0.0, 0.0, "未知位置", violationType)
                }
                .setNegativeButton("否", null)
                .show()
            return
        }

        saveRecord(location.latitude, location.longitude, currentAddress, violationType)
    }

    private fun saveRecord(lat: Double, lng: Double, address: String, type: String) {
        val now = System.currentTimeMillis()
        val record = ViolationRecord(
            timestamp = now,
            date = DateUtil.formatDate(now),
            time = DateUtil.formatTime(now),
            latitude = lat,
            longitude = lng,
            address = address,
            violationType = type
        )
        viewModel.insert(record)
        Toast.makeText(this, "違規記錄已儲存！", Toast.LENGTH_SHORT).show()
        binding.etViolationType.text?.clear()
    }
}
