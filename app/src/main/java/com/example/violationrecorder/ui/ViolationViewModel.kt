package com.example.violationrecorder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.violationrecorder.data.AppDatabase
import com.example.violationrecorder.data.ViolationRecord
import com.example.violationrecorder.data.ViolationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ViolationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ViolationRepository
    val allRecords: LiveData<List<ViolationRecord>>
    val allDates: LiveData<List<String>>

    init {
        val dao = AppDatabase.getDatabase(application).violationRecordDao()
        repository = ViolationRepository(dao)
        allRecords = repository.allRecords
        allDates = repository.allDates
    }

    fun getRecordsByDate(date: String): LiveData<List<ViolationRecord>> {
        return repository.getRecordsByDate(date)
    }

    fun getRecordsByDateRange(startDate: String, endDate: String): LiveData<List<ViolationRecord>> {
        return repository.getRecordsByDateRange(startDate, endDate)
    }

    fun insert(record: ViolationRecord) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(record)
    }

    fun update(record: ViolationRecord) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(record)
    }

    fun delete(record: ViolationRecord) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(record)
    }

    fun deleteById(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteById(id)
    }
}
