package com.example.violationrecorder.data

import androidx.lifecycle.LiveData

class ViolationRepository(private val dao: ViolationRecordDao) {

    val allRecords: LiveData<List<ViolationRecord>> = dao.getAllRecords()
    val allDates: LiveData<List<String>> = dao.getAllDates()

    fun getRecordsByDate(date: String): LiveData<List<ViolationRecord>> {
        return dao.getRecordsByDate(date)
    }

    fun getRecordsByDateRange(startDate: String, endDate: String): LiveData<List<ViolationRecord>> {
        return dao.getRecordsByDateRange(startDate, endDate)
    }

    suspend fun insert(record: ViolationRecord): Long {
        return dao.insert(record)
    }

    suspend fun insertAll(records: List<ViolationRecord>): List<Long> {
        return dao.insertAll(records)
    }

    suspend fun update(record: ViolationRecord) {
        dao.update(record)
    }

    suspend fun delete(record: ViolationRecord) {
        dao.delete(record)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun countByDate(date: String): Int {
        return dao.countByDate(date)
    }
}
