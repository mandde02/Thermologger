package com.thermoLogger

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ThermoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ThermoAccessibility"
        var instance: ThermoAccessibilityService? = null

        val THERMOMETER_PACKAGES = setOf(
            "com.google.android.GoogleCamera",
            "com.google.android.GoogleCameraEng",
            "com.google.pixel.measure",
            "com.google.android.apps.camera",
            "com.google.android.apps.cameralite"
        )

        val TEMP_PATTERN = Regex("""(\d{1,4}(?:\.\d)?)\s*[°º]?\s*([CcFf°])""")
        val TEMP_PATTERN_UNICODE = Regex("""(\d{1,4}(?:\.\d)?)\s*℃|(\d{1,4}(?:\.\d)?)\s*℉""")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected")

        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return

        val isThermometerApp = THERMOMETER_PACKAGES.any { pkg ->
            packageName.contains(pkg, ignoreCase = true)
        } || packageName.contains("camera", ignoreCase = true)
              || packageName.contains("measure", ignoreCase = true)
              || packageName.contains("thermometer", ignoreCase = true)

        if (!isThermometerApp) return

        val rootNode = rootInActiveWindow ?: return
        scanNodeForTemperature(rootNode)
        rootNode.recycle()
    }

    private fun scanNodeForTemperature(node: AccessibilityNodeInfo) {
        val text = node.text?.toString()

        if (!text.isNullOrBlank()) {
            val temp = parseTemperature(text)
            if (temp != null) {
                val reading = TemperatureReading(
                    temperatureCelsius = temp,
                    rawText = text
                )
                TemperatureRepository.addReading(reading)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanNodeForTemperature(child)
            child.recycle()
        }
    }

    fun parseTemperature(text: String): Double? {
        TEMP_PATTERN_UNICODE.find(text)?.let { match ->
            val celsius = match.groupValues[1].toDoubleOrNull()
            if (celsius != null) return celsius
            val fahrenheit = match.groupValues[2].toDoubleOrNull()
            if (fahrenheit != null) return fahrenheitToCelsius(fahrenheit)
        }

        val match = TEMP_PATTERN.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()

        return when {
            unit == "F" -> fahrenheitToCelsius(value)
            else -> value
        }
    }

    private fun fahrenheitToCelsius(f: Double): Double {
        return (f - 32.0) * 5.0 / 9.0
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
