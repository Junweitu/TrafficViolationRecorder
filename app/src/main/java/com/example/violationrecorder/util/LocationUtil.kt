package com.example.violationrecorder.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 定位工具：負責取得目前 GPS 座標，並反查地址（門牌號碼）
 */
object LocationUtil {

    private const val TAG = "LocationUtil"
    private const val LOCATION_TIMEOUT_MS = 10000L

    /**
     * 取得最後已知位置（快取，速度快但可能過期）
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(context: Context): Location? {
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    continuation.resume(location)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "取得 lastLocation 失敗", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }

    /**
     * 主動要求一次新的 GPS 定位（用於「重新定位」按鈕）
     * 會等待最多 10 秒取得精確位置
     */
    @SuppressLint("MissingPermission")
    suspend fun requestFreshLocation(context: Context): Location? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).apply {
            setMaxUpdates(1)
            setWaitForAccurateLocation(true)
        }.build()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    fusedLocationClient.removeLocationUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            }

            // 超時機制：10 秒後若仍無定位，回傳 null
            val timeoutRunnable = Runnable {
                fusedLocationClient.removeLocationUpdates(callback)
                if (continuation.isActive) {
                    Log.w(TAG, "定位逾時（10秒）")
                    continuation.resume(null)
                }
            }
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(timeoutRunnable, LOCATION_TIMEOUT_MS)

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                handler.removeCallbacks(timeoutRunnable)
                fusedLocationClient.removeLocationUpdates(callback)
                Log.e(TAG, "requestLocationUpdates 失敗", e)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }

            // 當 coroutine 被取消時清理
            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }
    }

    /**
     * 將經緯度反查為地址字串（包含門牌號碼）
     */
    suspend fun getAddressFromLocation(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String {
        if (!Geocoder.isPresent()) {
            return "座標：%.6f, %.6f".format(latitude, longitude)
        }

        return try {
            val geocoder = Geocoder(context, Locale.TRADITIONAL_CHINESE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用非同步 callback
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        if (cont.isActive) {
                            val addr = if (addresses.isNotEmpty()) {
                                formatAddress(addresses[0])
                            } else {
                                "座標：%.6f, %.6f".format(latitude, longitude)
                            }
                            cont.resume(addr)
                        }
                    }
                }
            } else {
                // Android 12 以下使用同步 API
                val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    formatAddress(addresses[0])
                } else {
                    "座標：%.6f, %.6f".format(latitude, longitude)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Geocoder 錯誤", e)
            "座標：%.6f, %.6f".format(latitude, longitude)
        }
    }

    /**
     * 格式化地址，優先呈現完整門牌號碼
     * 台灣地址格式：縣市 + 鄉鎮市區 + 里 + 街道 + 門牌號碼
     */
    private fun formatAddress(address: Address): String {
        val sb = StringBuilder()

        // 縣市（如：花蓮縣、台北市）
        address.adminArea?.let { sb.append(it) }

        // 鄉鎮市區（如：花蓮市、吉安鄉）
        address.locality?.let { sb.append(it) }

        // 里（如：主農里）— 有時 Geocoder 會放在 subLocality
        address.subLocality?.let { sb.append(it) }

        // 街道/路名（如：花東縱谷公路、中山路）
        address.thoroughfare?.let { sb.append(it) }

        // 門牌號碼（subThoroughfare，如：114）
        val houseNumber = address.subThoroughfare
        if (!houseNumber.isNullOrBlank()) {
            sb.append(houseNumber).append("號")
        } else {
            // 若 subThoroughfare 為空，嘗試從 featureName 擷取門牌號碼
            // featureName 有時會是 "(114)" 或 "114號" 之類的格式
            val feature = address.featureName
            if (!feature.isNullOrBlank()) {
                val num = extractHouseNumber(feature)
                if (num != null) {
                    sb.append(num).append("號")
                } else {
                    // 不是門牌號碼，當作建築物/地標附加在後面
                    sb.append("（").append(feature).append("）")
                }
            }
        }

        // 如果上面都沒東西，使用 addressLine 作為備援
        return if (sb.isNotEmpty()) {
            sb.toString()
        } else {
            address.getAddressLine(0) ?: "未知地址"
        }
    }

    /**
     * 從字串中擷取門牌號碼（支援 "(114)"、"114號"、"114" 等格式）
     */
    private fun extractHouseNumber(text: String): String? {
        // 移除括號
        val cleaned = text.trim().removeSurrounding("（", "）").removeSurrounding("(", ")")
        // 移除「號」
        val num = cleaned.removeSuffix("號").trim()
        // 檢查是否為純數字（可含「之」、「-」、「樓」等）
        return if (num.isNotEmpty() && num.any { it.isDigit() }) num else null
    }
}
