# 違規記錄器 (Traffic Violation Recorder)

一個簡單的 Android 應用程式，讓你在開車時遇到違規車輛，可以一鍵記錄當下的日期、時間、違規地點（門牌號碼及 GPS 座標）以及違規樣態，並支援依日期查詢清單與統計報表。

## 功能特色

- 📍 **一鍵記錄**：按下記錄按鈕，自動擷取目前 GPS 座標並反查地址（門牌號碼）
- 📝 **違規樣態**：自由輸入違規情形（如：紅線停車、逼車、危險駕駛等）
- 📅 **日期查詢**：依指定日期查看所有違規記錄清單
- 📊 **統計報表**：依日期區間產生統計報表，包含違規樣態與地點分布
- 💾 **本機儲存**：使用 Room 資料庫，所有資料儲存於手機本機，無需網路

## 記錄欄位

| 欄位 | 說明 |
|------|------|
| 日期 | 自動記錄，格式 yyyy-MM-dd |
| 時間 | 自動記錄，格式 HH:mm:ss |
| 違規地點 | 自動反查門牌號碼及地址 |
| 座標 | 自動記錄 GPS 經緯度 |
| 違規樣態 | 使用者手動輸入 |

## 技術架構

- **語言**：Kotlin
- **最低 SDK**：API 24 (Android 7.0)
- **目標 SDK**：API 34 (Android 14)
- **資料庫**：Room (SQLite)
- **定位**：Google Play Services Fused Location Provider
- **地址反查**：Android Geocoder
- **UI**：Material Design 3 + ViewBinding
- **架構**：ViewModel + LiveData + Repository

## 專案結構

```
app/src/main/java/com/example/violationrecorder/
├── data/
│   ├── ViolationRecord.kt      # 資料模型（Entity）
│   ├── ViolationRecordDao.kt   # 資料存取介面（DAO）
│   ├── AppDatabase.kt          # Room 資料庫
│   └── ViolationRepository.kt  # 資料倉儲
├── ui/
│   ├── MainActivity.kt         # 主畫面（記錄違規）
│   ├── RecordListActivity.kt   # 記錄清單（依日期查詢）
│   ├── ReportActivity.kt       # 統計報表
│   ├── ViolationViewModel.kt   # ViewModel
│   └── ViolationRecordAdapter.kt # 清單配接器
└── util/
    ├── DateUtil.kt             # 日期時間工具
    └── LocationUtil.kt         # 定位與地址反查工具
```

## 使用方式

### 1. 複製專案

```bash
git clone https://github.com/your-username/TrafficViolationRecorder.git
```

### 2. 使用 Android Studio 開啟

1. 開啟 Android Studio
2. 選擇 **File → Open**
3. 選擇 `TrafficViolationRecorder` 資料夾
4. 等待 Gradle 同步完成
5. 連接 Android 手機或啟用模擬器
6. 按下 **Run** 按鈕安裝並執行

### 3. 操作說明

1. **首次開啟**：App 會請求定位權限，請允許以取得 GPS 位置
2. **記錄違規**：
   - 確認頂端顯示的目前位置正確
   - 在「違規樣態」欄位輸入違規情形
   - 按下「記錄違規」按鈕
3. **查看清單**：點擊「依日期查看清單」，選擇日期即可查看當天所有記錄
4. **查看報表**：點擊「查看統計報表」，選擇日期區間即可產生統計報表

## 權限說明

| 權限 | 用途 |
|------|------|
| `ACCESS_FINE_LOCATION` | 取得精確 GPS 座標 |
| `ACCESS_COARSE_LOCATION` | 取得網路定位（備援） |
| `INTERNET` | Geocoder 地址反查 |

## 注意事項

- 地址反查需要網路連線，若無網路則僅記錄座標
- GPS 定位需在戶外或視野良好處才能精確取得
- 所有資料僅儲存於手機本機，移除 App 會一併刪除資料
- 開車時請注意安全，建議由乘客操作或停車後再記錄

## License

MIT License
