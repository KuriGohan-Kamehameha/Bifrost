package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import java.io.File
import kotlin.math.roundToInt

class CpuTemperatureAnimation(
    ledController: LedController,
    private val coolColorOverride: Int? = null,
    private val warmColorOverride: Int? = null,
    private val hotColorOverride: Int? = null
) : LedAnimation(ledController) {

    companion object {
        private const val UPDATE_INTERVAL_MS = 250L
        private const val TEMP_READ_INTERVAL_MS = 1000L
        private const val COLOR_LERP_FACTOR = 0.25f
        private const val BRIGHTNESS_LERP_FACTOR = 0.3f

        private val BLUE = Color.rgb(0, 120, 255)
        private val TEAL = Color.rgb(0, 185, 170)
        private val GREEN = Color.rgb(0, 220, 0)
        private val YELLOW = Color.rgb(255, 215, 0)
        private val ORANGE = Color.rgb(255, 140, 0)
        private val RED = Color.rgb(255, 0, 0)

        private val TEMP_POINTS = listOf(
            TempColorPoint(35f, BLUE),
            TempColorPoint(40f, TEAL),
            TempColorPoint(50f, GREEN),
            TempColorPoint(60f, YELLOW),
            TempColorPoint(65f, ORANGE),
            TempColorPoint(75f, RED)
        )
    }

    override val type: LedAnimationType = LedAnimationType.CPU_TEMPERATURE
    override val needsColorSelection: Boolean = false

    @Volatile
    private var isRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private val thermalReader = CpuThermalReader()

    // Background thread for sysfs file I/O so the main looper is never blocked
    private val thermalThread = HandlerThread("CpuThermalRead")
    private var thermalHandler: Handler? = null

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 255
    private var targetColor: Int = TEMP_POINTS.first().color
    private var currentColor: Int = TEMP_POINTS.first().color
    private var lastKnownTemperatureC: Float? = null
    private var lastTemperatureReadAt: Long = 0L

    /** Dispatches a thermal read to the background thread if the interval has elapsed. */
    private fun scheduleThermalRead() {
        val now = System.currentTimeMillis()
        if (now - lastTemperatureReadAt < TEMP_READ_INTERVAL_MS) return
        lastTemperatureReadAt = now   // mark dispatch time to prevent concurrent reads
        thermalHandler?.post {
            val raw = thermalReader.readCpuTemperatureC()
            handler.post {
                if (isRunning && raw != null) {
                    val q = quantizeToHalfDegree(raw)
                    lastKnownTemperatureC = q
                    targetColor = colorForTemperature(q)
                }
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            scheduleThermalRead()

            currentColor = lerpColor(currentColor, targetColor, COLOR_LERP_FACTOR)
            currentBrightness = lerpBrightnessInt(currentBrightness, targetBrightness, BRIGHTNESS_LERP_FACTOR)
            applyLeds(currentColor, currentBrightness)

            handler.postDelayed(this, adjustedAnimationDelay(UPDATE_INTERVAL_MS, targetBrightness))
        }
    }

    override fun start() {
        if (isRunning) return
        isRunning = true

        thermalThread.start()
        thermalHandler = Handler(thermalThread.looper)

        currentBrightness = targetBrightness

        // Initial temperature read on the background thread; start the update loop
        // once the first reading is available so the initial color is correct.
        thermalHandler!!.post {
            val raw = thermalReader.readCpuTemperatureC()
            handler.post {
                if (isRunning) {
                    if (raw != null) {
                        val q = quantizeToHalfDegree(raw)
                        lastKnownTemperatureC = q
                        targetColor = colorForTemperature(q)
                        currentColor = targetColor
                    }
                    lastTemperatureReadAt = System.currentTimeMillis()
                    applyLeds(currentColor, currentBrightness)
                    handler.post(updateRunnable)
                }
            }
        }
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        thermalHandler?.removeCallbacksAndMessages(null)
        thermalThread.quitSafely()
        thermalHandler = null
        currentBrightness = 0
        applyLeds(Color.BLACK, 0)
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    private fun quantizeToHalfDegree(temperatureC: Float): Float {
        return (temperatureC * 2f).roundToInt() / 2f
    }

    private fun colorForTemperature(temperatureC: Float): Int {
        if (coolColorOverride != null || warmColorOverride != null || hotColorOverride != null) {
            val cool = coolColorOverride ?: BLUE
            val warm = warmColorOverride ?: YELLOW
            val hot = hotColorOverride ?: RED

            return when {
                temperatureC <= 55f -> {
                    val factor = ((temperatureC - 35f) / 20f).coerceIn(0f, 1f)
                    lerpColor(cool, warm, factor)
                }
                else -> {
                    val factor = ((temperatureC - 55f) / 20f).coerceIn(0f, 1f)
                    lerpColor(warm, hot, factor)
                }
            }
        }

        val points = TEMP_POINTS
        if (temperatureC <= points.first().temperatureC) return points.first().color
        if (temperatureC >= points.last().temperatureC) return points.last().color

        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            if (temperatureC <= end.temperatureC) {
                val span = (end.temperatureC - start.temperatureC).coerceAtLeast(0.001f)
                val factor = ((temperatureC - start.temperatureC) / span).coerceIn(0f, 1f)
                return lerpColor(start.color, end.color, factor)
            }
        }

        return points.last().color
    }

    private fun applyLeds(color: Int, brightness: Int) {
        val gammaCorrectedBrightness = applyGamma(brightness)
        val scale = gammaCorrectedBrightness / 255f

        val red = (Color.red(color) * scale).roundToInt().coerceIn(0, 255)
        val green = (Color.green(color) * scale).roundToInt().coerceIn(0, 255)
        val blue = (Color.blue(color) * scale).roundToInt().coerceIn(0, 255)

        ledController.setLedColor(
            red,
            green,
            blue,
            leftTop = true,
            leftBottom = true,
            rightTop = true,
            rightBottom = true
        )
    }

    private data class TempColorPoint(
        val temperatureC: Float,
        val color: Int
    )
}

private class CpuThermalReader {

    companion object {
        private const val THERMAL_ROOT = "/sys/class/thermal"
        private const val MIN_VALID_TEMP_C = 5f
        private const val MAX_VALID_TEMP_C = 130f
        private const val MAX_ZONE_SCAN = 24
    }

    private var preferredTempFile: File? = null

    fun readCpuTemperatureC(): Float? {
        preferredTempFile?.let { cachedTempFile ->
            if (cachedTempFile.exists()) {
                parseTemperature(cachedTempFile)?.let { return it }
            }
            preferredTempFile = null
        }

        val thermalDir = File(THERMAL_ROOT)
        val zones = thermalDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("thermal_zone")
        } ?: return null

        var bestScore = Int.MIN_VALUE
        var bestTemp: Float? = null
        var bestTempFile: File? = null

        // Use a for loop with break so we actually stop after MAX_ZONE_SCAN zones.
        // forEach { return@forEach } is a continue, not a break, and would still
        // iterate every remaining zone doing nothing.
        var scanned = 0
        for (zone in zones) {
            if (scanned >= MAX_ZONE_SCAN) break
            scanned++
            val type = zone.resolve("type").safeReadText()?.trim()?.lowercase().orEmpty()
            val tempFile = zone.resolve("temp")
            val temperature = parseTemperature(tempFile) ?: continue
            val score = scoreZoneType(type)

            if (score > bestScore || (score == bestScore && (bestTemp == null || temperature > (bestTemp ?: Float.NEGATIVE_INFINITY)))) {
                bestScore = score
                bestTemp = temperature
                bestTempFile = tempFile
            }
        }

        if (bestTemp != null && bestTempFile != null) {
            preferredTempFile = bestTempFile
        }

        return bestTemp
    }

    private fun scoreZoneType(type: String): Int {
        var score = 0
        if (type.contains("cpu")) score += 10
        if (type.contains("soc") || type.contains("ap")) score += 4
        if (type.contains("x86_pkg") || type.contains("package")) score += 6
        if (type.contains("gpu") || type.contains("battery") || type.contains("skin")) score -= 8
        if (type.contains("charger") || type.contains("usb") || type.contains("quiet")) score -= 6
        return score
    }

    private fun parseTemperature(file: File): Float? {
        val raw = file.safeReadText()
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?: return null

        val numericValue = raw.toFloatOrNull() ?: return null
        val normalized = normalizeTemperature(numericValue)
        return normalized.takeIf { it in MIN_VALID_TEMP_C..MAX_VALID_TEMP_C }
    }

    private fun normalizeTemperature(rawValue: Float): Float {
        return when {
            rawValue > 1000f -> rawValue / 1000f
            rawValue > 200f -> rawValue / 10f
            else -> rawValue
        }
    }

    private fun File.safeReadText(): String? {
        return runCatching {
            inputStream().bufferedReader().use { it.readLine() }
        }.getOrNull()
    }
}