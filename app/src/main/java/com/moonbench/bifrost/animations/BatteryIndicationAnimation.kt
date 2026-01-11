package com.moonbench.bifrost.animations

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class BatteryIndicatorAnimation(
    ledController: LedController,
    private val context: Context
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.BATTERY_INDICATOR
    override val needsColorSelection: Boolean = false

    private var currentColor = Color.BLACK
    private var targetColor = Color.BLACK
    private var currentBrightness: Int = 255
    private var targetBrightness: Int = 255
    private var isBlinking = false
    private var blinkState = false

    @Volatile
    private var isRunning = false

    private val handler = Handler(Looper.getMainLooper())

    private val batteryCheckRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            checkBatteryAndUpdateTarget()
            handler.postDelayed(this, 60000)
        }
    }

    private val colorLerpRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (isBlinking) {
                blinkState = !blinkState
                val brightness = if (blinkState) targetBrightness else 0
                applyLeds(targetColor, brightness)
                handler.postDelayed(this, 500)
            } else {
                val lerpFactor = 0.15f
                currentColor = lerpColor(currentColor, targetColor, lerpFactor)
                currentBrightness = lerpInt(currentBrightness, targetBrightness, lerpFactor)

                applyLeds(currentColor, currentBrightness)

                val colorDiff = colorDistance(currentColor, targetColor)
                val brightnessDiff = kotlin.math.abs(currentBrightness - targetBrightness)

                if (colorDiff > 2 || brightnessDiff > 2) {
                    handler.postDelayed(this, 16)
                } else {
                    currentColor = targetColor
                    currentBrightness = targetBrightness
                    applyLeds(currentColor, currentBrightness)
                }
            }
        }
    }

    private fun restartLerpAnimation() {
        handler.removeCallbacks(colorLerpRunnable)
        handler.post(colorLerpRunnable)
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
        if (isRunning && !isBlinking) {
            restartLerpAnimation()
        }
    }

    override fun start() {
        if (isRunning) return
        isRunning = true
        checkBatteryAndUpdateTarget()
        handler.post(colorLerpRunnable)
        handler.postDelayed(batteryCheckRunnable, 60000)
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(batteryCheckRunnable)
        handler.removeCallbacks(colorLerpRunnable)
        currentBrightness = 0
        applyLeds(Color.BLACK, 0)
    }

    private fun checkBatteryAndUpdateTarget() {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).roundToInt()
        } else {
            100
        }

        val wasBlinking = isBlinking
        isBlinking = batteryPct <= 5

        targetColor = calculateColorForBattery(batteryPct)

        if (wasBlinking != isBlinking) {
            handler.removeCallbacks(colorLerpRunnable)
            handler.post(colorLerpRunnable)
        }
    }

    private fun calculateColorForBattery(percentage: Int): Int {
        return when {
            percentage > 50 -> {
                val factor = (percentage - 50) / 50f
                lerpColor(Color.rgb(255, 255, 0), Color.rgb(0, 255, 0), factor)
            }
            percentage > 20 -> {
                val factor = (percentage - 20) / 30f
                lerpColor(Color.rgb(255, 0, 0), Color.rgb(255, 255, 0), factor)
            }
            else -> {
                Color.rgb(255, 0, 0)
            }
        }
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val rDiff = kotlin.math.abs(Color.red(c1) - Color.red(c2))
        val gDiff = kotlin.math.abs(Color.green(c1) - Color.green(c2))
        val bDiff = kotlin.math.abs(Color.blue(c1) - Color.blue(c2))
        return rDiff + gDiff + bDiff
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
}