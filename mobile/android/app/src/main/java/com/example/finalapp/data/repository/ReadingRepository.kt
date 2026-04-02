package com.example.finalapp.data.repository

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.*

data class Reading(
    val id: Int,
    val systolic: String,
    val diastolic: String,
    val pulse: String,
    val spo2: String,
    val date: String,
    val time: String,
    val status: String
)

object ReadingRepository {
    // Shared list for all screens
    val readings = mutableStateListOf<Reading>()

    fun addReading(systolic: String, diastolic: String, pulse: String, spo2: String) {
        val sdfDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val currentDate = sdfDate.format(Date())
        val currentTime = sdfTime.format(Date())
        
        val status = if (systolic.toInt() > 130 || diastolic.toInt() > 85) "Elevated" else "Normal"

        readings.add(0, Reading(
            id = readings.size + 1,
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            spo2 = spo2,
            date = currentDate,
            time = currentTime,
            status = status
        ))
    }
    
    // Initial mock data if empty (or keep empty if user prefers)
    init {
        // addReading("120", "80", "72", "98") 
    }
}
