package com.example.violationrecorder.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtil {

    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val TIME_PATTERN = "HH:mm:ss"
    private const val DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

    private val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
    private val timeFormat = SimpleDateFormat(TIME_PATTERN, Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat(DATETIME_PATTERN, Locale.getDefault())

    /** 取得日期字串 yyyy-MM-dd */
    fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

    /** 取得時間字串 HH:mm:ss */
    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

    /** 取得日期時間字串 yyyy-MM-dd HH:mm:ss */
    fun formatDateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))

    /** 將 yyyy-MM-dd 轉為顯示用格式（yyyy 年 MM 月 dd 日） */
    fun formatDisplayDate(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr)
            SimpleDateFormat("yyyy 年 MM 月 dd 日", Locale.TRADITIONAL_CHINESE).format(date!!)
        } catch (e: Exception) {
            dateStr
        }
    }

    /** 取得今天的日期字串 */
    fun today(): String = dateFormat.format(Date())
}
