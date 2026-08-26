package com.example.violationrecorder.util

import com.example.violationrecorder.data.ViolationRecord
import org.dhatim.fastexcel.Workbook
import java.io.OutputStream

/**
 * Excel 匯出工具：將違規記錄列表寫入 .xlsx 檔案
 * 使用 fastexcel 輕量級函式庫，相容 Android（無 AWT 相依）
 */
object ExcelExporter {

    private const val APP_NAME = "違規記錄器"
    private const val APP_VERSION = "1.0"

    // 欄位標題
    private val HEADERS = listOf(
        "日期",
        "時間",
        "地址",
        "緯度",
        "經度",
        "違規樣態"
    )

    /**
     * 將違規記錄匯出為 xlsx 格式，寫入指定的 OutputStream
     * @param outputStream 目標檔案串流（由 SAF 提供）
     * @param records 要匯出的記錄列表
     * @param sheetName 工作表名稱（預設「違規記錄」）
     */
    fun exportToExcel(
        outputStream: OutputStream,
        records: List<ViolationRecord>,
        sheetName: String = "違規記錄"
    ) {
        val wb = Workbook(outputStream, APP_NAME, APP_VERSION)
        val ws = wb.newWorksheet(sheetName)

        // === 表頭（第 0 列）===
        val headerStyle = wb.newStyle()
            .bold()
            .fillColor("D9E1F2") // 淺藍色底
            .borderStyle(org.dhatim.fastexcel.BorderSide.TOP, org.dhatim.fastexcel.BorderStyle.THIN)
            .borderStyle(org.dhatim.fastexcel.BorderSide.BOTTOM, org.dhatim.fastexcel.BorderStyle.THIN)
            .borderStyle(org.dhatim.fastexcel.BorderSide.LEFT, org.dhatim.fastexcel.BorderStyle.THIN)
            .borderStyle(org.dhatim.fastexcel.BorderSide.RIGHT, org.dhatim.fastexcel.BorderStyle.THIN)
            .set()

        HEADERS.forEachIndexed { colIndex, header ->
            ws.value(0, colIndex, header)
            ws.style(0, colIndex, headerStyle)
        }

        // === 資料列（從第 1 列開始）===
        val cellStyle = wb.newStyle()
            .borderStyle(org.dhatim.fastexcel.BorderSide.TOP, org.dhatim.fastexcel.BorderStyle.THIN)
            .borderStyle(org.dhatim.fastexcel.BorderSide.BOTTOM, org.dhatim.fastexcel.BorderStyle.THIN)
            .borderStyle(org.dhatim.fastexcel.BorderSide.LEFT, org.dhatim.fastexcel.BorderStyle.THIN)
            .borderStyle(org.dhatim.fastexcel.BorderSide.RIGHT, org.dhatim.fastexcel.BorderStyle.THIN)
            .wrapText(true)
            .set()

        records.forEachIndexed { rowIndex, record ->
            val row = rowIndex + 1
            ws.value(row, 0, record.date)
            ws.value(row, 1, record.time)
            ws.value(row, 2, record.address)
            ws.value(row, 3, record.latitude)
            ws.value(row, 4, record.longitude)
            ws.value(row, 5, record.violationType)

            for (col in HEADERS.indices) {
                ws.style(row, col, cellStyle)
            }
        }

        // === 設定欄寬 ===
        ws.width(0, 12)  // 日期
        ws.width(1, 10)  // 時間
        ws.width(2, 40)  // 地址
        ws.width(3, 12)  // 緯度
        ws.width(4, 12)  // 經度
        ws.width(5, 30)  // 違規樣態

        // === 凍結表頭 ===
        ws.freezeTopRow(1)

        wb.finish()
    }

    /**
     * 產生建議的檔名：違規記錄_yyyy-MM-dd.xlsx
     */
    fun suggestFileName(date: String): String {
        return "違規記錄_${date}.xlsx"
    }
}
