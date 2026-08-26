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
     * 格式化地址
     * 優先使用 Geocoder 提供的完整格式化地址（getAddressLine(0)），
     * 因為它已經正確處理了門牌號碼、公路里程等細節。
     * 只有在 getAddressLine(0) 為空時，才手動拼湊備援地址。
     */
    private fun formatAddress(address: Address): String {
        // 優先使用 Geocoder 已格式化的完整地址
        val fullAddress = address.getAddressLine(0)
        if (!fullAddress.isNullOrBlank()) {
            return fullAddress
        }

        // 備援：手動拼湊（僅在 getAddressLine 為空時使用）
        val sb = StringBuilder()

        // 縣市
        address.adminArea?.let { sb.append(it) }
        // 鄉鎮市區
        address.locality?.let { sb.append(it) }
        // 里
        address.subLocality?.let { sb.append(it) }
        // 街道/路名
        address.thoroughfare?.let { sb.append(it) }

        // 門牌號碼：只有 subThoroughfare 才是真正的門牌號碼
        // 注意：featureName 可能是公路里程數（如「114」代表114公里處），不是門牌號碼，不可亂加「號」
        val houseNumber = address.subThoroughfare
        if (!houseNumber.isNullOrBlank()) {
            sb.append(houseNumber).append("號")
        }

        // 特徵名稱（如建築物、地名）附加在括號中
        val feature = address.featureName
        if (!feature.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append("（").append(feature).append("）")
            else sb.append(feature)
        }

        return if (sb.isNotEmpty()) sb.toString() else "未知地址"
    }
}
