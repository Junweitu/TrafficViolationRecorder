package com.example.violationrecorder.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ViolationRecordDao {

    @Insert
    suspend fun insert(record: ViolationRecord): Long

    @Update
    suspend fun update(record: ViolationRecord)

    @Delete
    suspend fun delete(record: ViolationRecord)

    @Query("SELECT * FROM violation_records ORDER BY timestamp DESC")
    fun getAllRecords(): LiveData<List<ViolationRecord>>

    /** 依日期查詢（日期格式 yyyy-MM-dd） */
    @Query("SELECT * FROM violation_records WHERE date = :date ORDER BY timestamp DESC")
    fun getRecordsByDate(date: String): LiveData<List<ViolationRecord>>

    /** 依日期區間查詢 */
    @Query("SELECT * FROM violation_records WHERE date BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getRecordsByDateRange(startDate: String, endDate: String): LiveData<List<ViolationRecord>>

    /** 取得所有有記錄的日期（用於日期選擇器） */
    @Query("SELECT DISTINCT date FROM violation_records ORDER BY date DESC")
    fun getAllDates(): LiveData<List<String>>

    @Query("SELECT COUNT(*) FROM violation_records WHERE date = :date")
    suspend fun countByDate(date: String): Int

    @Query("DELETE FROM violation_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
