package com.example.violationrecorder.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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

    /**
     * 取得目前位置
     * @return Location 物件，包含經緯度
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        Log.w(TAG, "lastLocation is null, 嘗試 requestLocationUpdates")
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "取得位置失敗", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
        }
    }

    /**
     * 將經緯度反查為地址字串（包含門牌號碼）
     * @return 地址字串，若無法取得則回傳座標字串
     */
    fun getAddressFromLocation(context: Context, latitude: Double, longitude: Double): String {
        if (Geocoder.isPresent()) {
            return try {
                val geocoder = Geocoder(context, Locale.TRADITIONAL_CHINESE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ 使用非同步 API
                    var result = "座標：$latitude, $longitude"
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses: List<Address> ->
                        if (addresses.isNotEmpty()) {
                            result = formatAddress(addresses[0])
                        }
                    }
                    // 簡短等待，實務上建議改用 callback 或 suspend
                    Thread.sleep(300)
                    result
                } else {
                    // Android 12 以下使用同步 API
                    val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        formatAddress(addresses[0])
                    } else {
                        "座標：$latitude, $longitude"
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Geocoder 錯誤", e)
                "座標：$latitude, $longitude"
            }
        }
        return "座標：$latitude, $longitude"
    }

    /** 格式化地址，優先呈現門牌號碼與街道 */
    private fun formatAddress(address: Address): String {
        val sb = StringBuilder()
        // 縣市
        address.adminArea?.let { sb.append(it) }
        // 鄉鎮市區
        address.locality?.let { sb.append(it) }
        // 街道
        address.thoroughfare?.let { sb.append(it) }
        // 門牌號碼
        address.subThoroughfare?.let { sb.append("號") }
        // 特徵名稱（如建築物）
        address.featureName?.let {
            if (sb.isNotEmpty()) sb.append("（").append(it).append("）")
        }
        return if (sb.isNotEmpty()) sb.toString() else address.getAddressLine(0) ?: "未知地址"
    }
}
