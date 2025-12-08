package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class PulseAnimation(
    ledController: LedController,
    initialColor: Int
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.PULSE
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var targetColor: Int = initialColor
    private var currentColor: Int = initialColor
    private var targetBrightness: Int = 255
    private var speed: Float = 0.5f
    private var isOn = false
    private var counter = 0

    override fun setTargetColor(color: Int) {
        targetColor = color
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0f, 1f)
    }

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return

            val colorFactor = 0.05f + 0.45f * speed
            currentColor = lerpColor(currentColor, targetColor, colorFactor)

            val baseR = Color.red(currentColor)
            val baseG = Color.green(currentColor)
            val baseB = Color.blue(currentColor)

            val globalScale = targetBrightness / 255f

            val pulseDuration = (40 - 30 * speed).toInt()

            val factor = if (isOn) globalScale else 0f

            val r = (baseR * factor).roundToInt().coerceIn(0, 255)
            val g = (baseG * factor).roundToInt().coerceIn(0, 255)
            val b = (baseB * factor).roundToInt().coerceIn(0, 255)

            ledController.setLedColor(
                r,
                g,
                b,
                leftTop = true,
                leftBottom = true,
                rightTop = true,
                rightBottom = true
            )

            counter++
            if (counter >= pulseDuration) {
                isOn = !isOn
                counter = 0
            }

            handler.postDelayed(this, 30L)
        }
    }

    override fun start() {
        if (running) return
        running = true
        handler.post(runnable)
    }

    override fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        ledController.setLedColor(
            0,
            0,
            0,
            leftTop = true,
            leftBottom = true,
            rightTop = true,
            rightBottom = true
        )
    }
}