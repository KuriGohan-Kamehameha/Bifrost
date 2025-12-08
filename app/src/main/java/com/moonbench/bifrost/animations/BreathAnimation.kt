package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt
import kotlin.math.sin

class BreathAnimation(
    ledController: LedController,
    initialColor: Int
) : LedAnimation(ledController) {
    override val type: LedAnimationType = LedAnimationType.BREATH
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var targetColor: Int = initialColor
    private var currentColor: Int = initialColor
    private var targetBrightness: Int = 255
    private var speed: Float = 0.5f
    private var phase = 0.0

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

            val breath = ((sin(phase) + 1.0) / 2.0).coerceIn(0.0, 1.0)
            val minFactor = 0.1
            val maxFactor = 1.0
            val breathFactor = (minFactor + (maxFactor - minFactor) * breath) * globalScale

            val r = (baseR * breathFactor).roundToInt().coerceIn(0, 255)
            val g = (baseG * breathFactor).roundToInt().coerceIn(0, 255)
            val b = (baseB * breathFactor).roundToInt().coerceIn(0, 255)

            ledController.setLedColor(
                r,
                g,
                b,
                leftTop = true,
                leftBottom = true,
                rightTop = true,
                rightBottom = true
            )

            val speedFactor = 0.02 + 0.18 * speed
            phase += speedFactor

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