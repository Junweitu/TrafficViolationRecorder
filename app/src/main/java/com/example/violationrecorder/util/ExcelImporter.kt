package com.example.violationrecorder.util

import android.util.Xml
import com.example.violationrecorder.data.ViolationRecord
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Excel 匯入工具：從 .xlsx 檔案讀取違規記錄
 * 使用 Android 內建 API（ZipInputStream + XmlPullParser）解析 xlsx，不需額外函式庫
 *
 * 預期欄位（第一列為表頭）：日期 | 時間 | 地址 | 緯度 | 經度 | 違規樣態
 */
object ExcelImporter {

    private const val TAG = "ExcelImporter"

    // 匯入時的日期時間格式
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 從 InputStream 讀取 xlsx 檔案，解析為違規記錄列表
     * @param inputStream xlsx 檔案串流
     * @return 解析成功的記錄列表（已跳過表頭列）
     */
    fun importFromExcel(inputStream: InputStream): List<ViolationRecord> {
        val records = mutableListOf<ViolationRecord>()

        ZipInputStream(inputStream).use { zip ->
            var sharedStrings: List<String>? = null
            var sheetData: List<List<String>>? = null

            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        sharedStrings = parseSharedStrings(zip)
                    }
                    entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") -> {
                        sheetData = parseWorksheet(zip, sharedStrings ?: emptyList())
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            if (sheetData != null && sheetData.size > 1) {
                // 跳過第一列（表頭），其餘為資料
                for (i in 1 until sheetData.size) {
                    val row = sheetData[i]
                    val record = parseRow(row)
                    if (record != null) {
                        records.add(record)
                    }
                }
            }
        }

        return records
    }

    /**
     * 解析 sharedStrings.xml，取得共用字串列表
     */
    private fun parseSharedStrings(inputStream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        currentText = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    currentText.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        strings.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    /**
     * 解析工作表 XML，傳回二維陣列（列 × 欄）
     */
    private fun parseWorksheet(inputStream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentRow = mutableListOf<String>()
        var currentCellValue = StringBuilder()
        var isSharedString = false
        var currentCellIndex = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRow = mutableListOf()
                        }
                        "c" -> {
                            // 檢查儲存格類型：t="s" 表示共用字串
                            isSharedString = parser.getAttributeValue(null, "t") == "s"
                            currentCellValue = StringBuilder()
                            // 取得欄位索引（從參考位址如 A1, B2 解析）
                            val ref = parser.getAttributeValue(null, "r")
                            currentCellIndex = ref?.let { parseColumnIndex(it) } ?: currentRow.size
                        }
                        "v" -> {
                            currentCellValue = StringBuilder()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    currentCellValue.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> {
                            val rawValue = currentCellValue.toString()
                            val value = if (isSharedString) {
                                val idx = rawValue.toIntOrNull()
                                if (idx != null && idx < sharedStrings.size) sharedStrings[idx] else rawValue
                            } else {
                                rawValue
                            }
                            // 填補空缺的欄位
                            while (currentRow.size < currentCellIndex) {
                                currentRow.add("")
                            }
                            if (currentRow.size == currentCellIndex) {
                                currentRow.add(value)
                            } else {
                                currentRow[currentCellIndex] = value
                            }
                        }
                        "row" -> {
                            rows.add(currentRow)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    /**
     * 從儲存格參考位址（如 A1, B2, AA3）解析欄位索引（0-based）
     */
    private fun parseColumnIndex(ref: String): Int {
        var index = 0
        for (c in ref) {
            if (c.isLetter()) {
                index = index * 26 + (c.uppercaseChar() - 'A' + 1)
            } else {
                break
            }
        }
        return index - 1
    }

    /**
     * 將一列資料解析為 ViolationRecord
     * 欄位順序：日期(0) | 時間(1) | 地址(2) | 緯度(3) | 經度(4) | 違規樣態(5)
     */
    private fun parseRow(row: List<String>): ViolationRecord? {
        if (row.size < 6) return null

        val date = row.getOrNull(0)?.trim() ?: return null
        val time = row.getOrNull(1)?.trim() ?: "00:00:00"
        val address = row.getOrNull(2)?.trim() ?: ""
        val latitude = row.getOrNull(3)?.trim()?.toDoubleOrNull() ?: 0.0
        val longitude = row.getOrNull(4)?.trim()?.toDoubleOrNull() ?: 0.0
        val violationType = row.getOrNull(5)?.trim() ?: ""

        if (date.isEmpty() || violationType.isEmpty()) return null

        // 嘗試組合日期時間為 timestamp
        val timestamp = try {
            dateTimeFormat.parse("$date $time")?.time ?: Date().time
        } catch (e: Exception) {
            try {
                dateFormat.parse(date)?.time ?: Date().time
            } catch (_: Exception) {
                Date().time
            }
        }

        return ViolationRecord(
            timestamp = timestamp,
            date = date,
            time = time,
            latitude = latitude,
            longitude = longitude,
            address = address,
            violationType = violationType
        )
    }
}
