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
        // 重新整理位置
        binding.btnRefreshLocation.setOnClickListener {
            checkLocationPermissionAndFetch()
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

    private fun checkLocationPermissionAndFetch() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                fetchLocation()
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
    private fun fetchLocation() {
        binding.tvLocationStatus.text = "定位中..."
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val location = withContext(Dispatchers.IO) {
                    LocationUtil.getCurrentLocation(this@MainActivity)
                }
                if (location != null) {
                    currentLocation = location
                    val address = withContext(Dispatchers.IO) {
                        LocationUtil.getAddressFromLocation(
                            this@MainActivity,
                            location.latitude,
                            location.longitude
                        )
                    }
                    currentAddress = address
                    updateLocationUI(location, address)
                } else {
                    binding.tvLocationStatus.text = "無法取得位置，請確認 GPS 已開啟"
                    Toast.makeText(this@MainActivity, "無法取得位置，請稍後再試", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.tvLocationStatus.text = "定位失敗：${e.message}"
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
        binding.etViolationType.text.clear()
    }
}
