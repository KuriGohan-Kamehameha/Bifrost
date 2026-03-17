package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class StaticAnimation(
    ledController: LedController,
    initialColor: Int,
    initialRightColor: Int = initialColor
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.STATIC
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var targetColor: Int = initialColor
    private var currentColor: Int = initialColor

    private var targetRightColor: Int = initialRightColor
    private var currentRightColor: Int = initialRightColor

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 255

    private var lerpStrength: Float = 0.5f

    // True while the runnable is posted to the handler; prevents double-posting
    // and allows setters to restart the loop after it has paused on convergence.
    private var loopPending = false

    private fun ensureLooping() {
        if (running && !loopPending) {
            loopPending = true
            handler.post(runnable)
        }
    }

    override fun setTargetColor(color: Int) {
        targetColor = color
        ensureLooping()
    }

    override fun setTargetRightColor(color: Int) {
        targetRightColor = color
        ensureLooping()
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
        ensureLooping()
    }

    override fun setLerpStrength(strength: Float) {
        lerpStrength = strength.coerceIn(0f, 1f)
        ensureLooping()
    }

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return

            val factor = 0.15f + 0.7f * lerpStrength
            currentColor = lerpColor(currentColor, targetColor, factor)
            currentRightColor = lerpColor(currentRightColor, targetRightColor, factor)
            currentBrightness = lerpBrightnessInt(currentBrightness, targetBrightness, factor)

            // Apply gamma correction consistent with other animations
            val scale = applyGamma(currentBrightness) / 255f

            val lr = (Color.red(currentColor) * scale).roundToInt().coerceIn(0, 255)
            val lg = (Color.green(currentColor) * scale).roundToInt().coerceIn(0, 255)
            val lb = (Color.blue(currentColor) * scale).roundToInt().coerceIn(0, 255)

            val rr = (Color.red(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
            val rg = (Color.green(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
            val rb = (Color.blue(currentRightColor) * scale).roundToInt().coerceIn(0, 255)

            ledController.setLedColor(lr, lg, lb,
                leftTop = true, leftBottom = true,
                rightTop = false, rightBottom = false)
            ledController.setLedColor(rr, rg, rb,
                leftTop = false, leftBottom = false,
                rightTop = true, rightBottom = true)

            // Stop looping once fully converged to avoid redundant 30 ms ticks at idle.
            // Setters will restart the loop when any target changes.
            if (currentColor == targetColor && currentRightColor == targetRightColor &&
                    currentBrightness == targetBrightness) {
                loopPending = false
            } else {
                handler.postDelayed(this, adjustedAnimationDelay(30L, targetBrightness))
            }
        }
    }

    override fun start() {
        if (running) return
        running = true
        loopPending = true
        handler.post(runnable)
    }

    override fun stop() {
        running = false
        loopPending = false
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
