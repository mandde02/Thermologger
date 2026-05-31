package com.thermoLogger

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.thermoLogger.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val chartEntries = mutableListOf<Entry>()
    private lateinit var lineDataSet: LineDataSet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart()
        setupObservers()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupChart() {
        val chart = binding.lineChart

        lineDataSet = LineDataSet(chartEntries, "Temperature (°C)").apply {
            color = Color.parseColor("#FF6B35")
            setCircleColor(Color.parseColor("#FF6B35"))
            circleRadius = 3f
            lineWidth = 2f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            setDrawFilled(true)
            fillColor = Color.parseColor("#FF6B35")
            fillAlpha = 40
        }

        chart.apply {
            data = LineData(lineDataSet)
            description.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            setGridBackgroundColor(Color.TRANSPARENT)
            setDrawGridBackground(false)
            setBorderColor(Color.parseColor("#30FFFFFF"))
            setBorderWidth(1f)
            setDrawBorders(true)

            legend.apply {
                textColor = Color.parseColor("#8B949E")
                textSize = 11f
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#20FFFFFF")
                axisLineColor = Color.parseColor("#30FFFFFF")
                textSize = 9f
                setAvoidFirstLastClipping(true)
                valueFormatter = object : ValueFormatter() {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        return sdf.format(Date(value.toLong()))
                    }
                }
                labelCount = 4
            }

            axisLeft.apply {
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#20FFFFFF")
                axisLineColor = Color.parseColor("#30FFFFFF")
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "%.1f°".format(value)
                }
            }

            axisRight.isEnabled = false
            isDoubleTapToZoomEnabled = false
            setPinchZoom(true)
            setScaleEnabled(true)
        }
    }

    private fun setupObservers() {
        TemperatureRepository.latestReading.observe(this) { reading ->
            reading ?: return@observe

            binding.tvCurrentTemp.text = "%.1f°C".format(reading.temperatureCelsius)
            binding.tvLastUpdated.text = "Updated: ${reading.getFormattedTime()}"

            if (TemperatureRepository.isRecording.value == true) {
                val entry = Entry(reading.timestamp.toFloat(), reading.temperatureCelsius.toFloat())
                chartEntries.add(entry)

                if (chartEntries.size > 200) chartEntries.removeAt(0)

                lineDataSet.notifyDataSetChanged()
                binding.lineChart.data.notifyDataChanged()
                binding.lineChart.notifyDataSetChanged()
                binding.lineChart.invalidate()
                binding.lineChart.moveViewToX(entry.x)
            }

            updateStats()
        }

        TemperatureRepository.isRecording.observe(this) { recording ->
            if (recording) {
                binding.btnStartStop.text = "⏹ STOP"
                binding.btnStartStop.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#FF4444"))
                binding.tvRecordingStatus.text = "● RECORDING"
                binding.tvRecordingStatus.setTextColor(Color.parseColor("#FF4444"))
            } else {
                binding.btnStartStop.text = "▶ START"
                binding.btnStartStop.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#3FB950"))
                binding.tvRecordingStatus.text = "● NOT RECORDING"
                binding.tvRecordingStatus.setTextColor(Color.parseColor("#8B949E"))
            }
        }
    }

    private fun setupButtons() {
        binding.btnStartStop.setOnClickListener {
            if (TemperatureRepository.isRecording.value == true) {
                TemperatureRepository.stopRecording()
                val count = TemperatureRepository.readings.size
                Toast.makeText(this, "Stopped. $count readings logged.", Toast.LENGTH_SHORT).show()
            } else {
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(this, "Please enable the Accessibility Service first!", Toast.LENGTH_LONG).show()
                    openAccessibilitySettings()
                    return@setOnClickListener
                }
                chartEntries.clear()
                lineDataSet.notifyDataSetChanged()
                binding.lineChart.data.notifyDataChanged()
                binding.lineChart.notifyDataSetChanged()
                binding.lineChart.invalidate()

                TemperatureRepository.startRecording()
                Toast.makeText(this, "Recording started! Open the Thermometer app.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClear.setOnClickListener {
            TemperatureRepository.clearAll()
            chartEntries.clear()
            lineDataSet.notifyDataSetChanged()
            binding.lineChart.data.notifyDataChanged()
            binding.lineChart.notifyDataSetChanged()
            binding.lineChart.invalidate()
            binding.tvCurrentTemp.text = "--.-°C"
            binding.tvLastUpdated.text = "Waiting for Thermometer app..."
            updateStats()
            Toast.makeText(this, "Cleared.", Toast.LENGTH_SHORT).show()
        }

        binding.btnExportCsv.setOnClickListener {
            val readings = TemperatureRepository.readings.toList()
            if (readings.isEmpty()) {
                Toast.makeText(this, "No readings to export.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uri = ExportManager.exportCsv(this, readings)
            if (uri != null) {
                ExportManager.shareFile(this, uri, "text/csv")
            } else {
                Toast.makeText(this, "Export failed.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnExportXlsx.setOnClickListener {
            val readings = TemperatureRepository.readings.toList()
            if (readings.isEmpty()) {
                Toast.makeText(this, "No readings to export.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Generating XLSX...", Toast.LENGTH_SHORT).show()
            Thread {
                val uri = ExportManager.exportXlsx(this, readings)
                runOnUiThread {
                    if (uri != null) {
                        ExportManager.shareFile(this, uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    } else {
                        Toast.makeText(this, "XLSX export failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        binding.btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    private fun updateStats() {
        val (min, avg, max) = TemperatureRepository.getStats()
        binding.tvMin.text = if (min != null) "%.1f°".format(min) else "---"
        binding.tvAvg.text = if (avg != null) "%.1f°".format(avg) else "---"
        binding.tvMax.text = if (max != null) "%.1f°".format(max) else "---"
        binding.tvCount.text = TemperatureRepository.readings.size.toString()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            binding.tvServiceStatus.text = "🟢 Service On"
            binding.tvServiceStatus.setTextColor(Color.parseColor("#3FB950"))
        } else {
            binding.tvServiceStatus.text = "⚪ Service Off"
            binding.tvServiceStatus.setTextColor(Color.parseColor("#8B949E"))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/${ThermoAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Open Settings > Accessibility manually.", Toast.LENGTH_LONG).show()
        }
    }
}
