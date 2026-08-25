package com.example.violationrecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 違規記錄資料模型
 * @param id              唯一識別碼（自動產生）
 * @param timestamp       記錄時間戳（毫秒，System.currentTimeMillis()）
 * @param date            日期字串，格式 yyyy-MM-dd，方便依日期查詢
 * @param time            時間字串，格式 HH:mm:ss
 * @param latitude        緯度
 * @param longitude       經度
 * @param address         門牌號碼 / 地址（由 Geocoder 反查）
 * @param violationType   違規樣態（使用者手動輸入）
 */
@Entity(tableName = "violation_records")
data class ViolationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val date: String,
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val violationType: String
)
