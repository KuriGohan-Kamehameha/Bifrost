package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class FadeTransitionAnimation(
    ledController: LedController,
    initialColor: Int,
    private val secondColor: Int = Color.rgb(0, 255, 255)
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.FADE_TRANSITION
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var targetColor: Int = initialColor
    private var targetBrightness: Int = 255
    private var speed: Float = 0.5f
    private var progress = 0f
    private var direction = 1

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

            val currentColor = lerpColor(targetColor, secondColor, progress)

            val baseR = Color.red(currentColor)
            val baseG = Color.green(currentColor)
            val baseB = Color.blue(currentColor)

            val globalScale = targetBrightness / 255f

            val r = (baseR * globalScale).roundToInt().coerceIn(0, 255)
            val g = (baseG * globalScale).roundToInt().coerceIn(0, 255)
            val b = (baseB * globalScale).roundToInt().coerceIn(0, 255)

            ledController.setLedColor(
                r,
                g,
                b,
                leftTop = true,
                leftBottom = true,
                rightTop = true,
                rightBottom = true
            )

            val speedFactor = 0.01f + 0.04f * speed
            progress += speedFactor * direction

            if (progress >= 1f) {
                progress = 1f
                direction = -1
            } else if (progress <= 0f) {
                progress = 0f
                direction = 1
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