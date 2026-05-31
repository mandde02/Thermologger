package com.thermoLogger

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TemperatureReading(
    val timestamp: Long = System.currentTimeMillis(),
    val temperatureCelsius: Double,
    val rawText: String = ""
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getFormattedDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
