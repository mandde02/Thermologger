package com.thermoLogger

import androidx.lifecycle.MutableLiveData

object TemperatureRepository {
    val readings = mutableListOf<TemperatureReading>()
    val latestReading = MutableLiveData<TemperatureReading?>()
    val isRecording = MutableLiveData<Boolean>(false)

    private var lastValue: Double? = null
    private var lastTimestamp: Long = 0

    fun addReading(reading: TemperatureReading) {
        val now = System.currentTimeMillis()
        val isDifferentValue = reading.temperatureCelsius != lastValue
        val enoughTimePassed = (now - lastTimestamp) >= 2000

        if (isRecording.value == true && (isDifferentValue || enoughTimePassed)) {
            readings.add(reading)
            lastValue = reading.temperatureCelsius
            lastTimestamp = now
            latestReading.postValue(reading)
        } else if (isRecording.value != true) {
            latestReading.postValue(reading)
        }
    }

    fun startRecording() {
        readings.clear()
        lastValue = null
        lastTimestamp = 0
        isRecording.postValue(true)
    }

    fun stopRecording() {
        isRecording.postValue(false)
    }

    fun clearAll() {
        readings.clear()
        lastValue = null
        lastTimestamp = 0
        latestReading.postValue(null)
    }

    fun getStats(): Triple<Double?, Double?, Double?> {
        if (readings.isEmpty()) return Triple(null, null, null)
        val temps = readings.map { it.temperatureCelsius }
        return Triple(temps.minOrNull(), temps.average(), temps.maxOrNull())
    }
}
