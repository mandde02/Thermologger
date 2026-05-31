package com.thermoLogger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    private fun getExportDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ThermoLogger")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    fun exportCsv(context: Context, readings: List<TemperatureReading>): Uri? {
        return try {
            val file = File(getExportDir(context), "ThermoLogger_${getTimestamp()}.csv")
            FileWriter(file).use { writer ->
                writer.appendLine("Timestamp,DateTime,Temperature_C,Temperature_F,Raw_Text")
                readings.forEach { r ->
                    val tempF = r.temperatureCelsius * 9.0 / 5.0 + 32.0
                    writer.appendLine(
                        "${r.timestamp}," +
                        "\"${r.getFormattedDateTime()}\"," +
                        "%.2f,".format(r.temperatureCelsius) +
                        "%.2f,".format(tempF) +
                        "\"${r.rawText.replace("\"", "'")}\""
                    )
                }
            }
            getUriForFile(context, file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportXlsx(context: Context, readings: List<TemperatureReading>): Uri? {
        return try {
            val file = File(getExportDir(context), "ThermoLogger_${getTimestamp()}.xlsx")
            val workbook = XSSFWorkbook()

            val dataSheet = workbook.createSheet("Temperature Log")

            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.DARK_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                val font = workbook.createFont().apply {
                    bold = true
                    color = IndexedColors.WHITE.index
                    fontHeightInPoints = 11
                }
                setFont(font)
                alignment = HorizontalAlignment.CENTER
            }

            val dataStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
            }

            val altStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
            }

            val tempFormat = workbook.createDataFormat().getFormat("0.00")
            val tempStyle = workbook.createCellStyle().apply {
                dataFormat = tempFormat
                alignment = HorizontalAlignment.CENTER
            }

            val headers = listOf("#", "Timestamp (ms)", "Date & Time", "Temp (°C)", "Temp (°F)", "Raw Text")
            val headerRow = dataSheet.createRow(0)
            headers.forEachIndexed { i, h ->
                headerRow.createCell(i).apply {
                    setCellValue(h)
                    cellStyle = headerStyle
                }
            }

            readings.forEachIndexed { idx, r ->
                val row = dataSheet.createRow(idx + 1)
