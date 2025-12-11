package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import com.moonbench.bifrost.tools.AudioAnalyzer
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class AudioReactiveAnimation(
    ledController: LedController,
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val baseColor: Int,
    private val profile: PerformanceProfile
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.AUDIO_REACTIVE
    override val needsColorSelection: Boolean = true

    private var audioAnalyzer: AudioAnalyzer? = null

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 0
    private var response: Float = 0.5f
    private var sensitivity: Float = 0.5f
    private var smoothedIntensity: Float = 0f

    private val hasNewBrightnessData = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)

    @Volatile
    private var cachedIntensity: Float = 0f

    private var animationThread: HandlerThread? = null
    private var animationHandler: Handler? = null

    private val updateInterval: Long
        get() = if (profile.intervalMs == 0L) 16L else profile.intervalMs

    private val ledUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return

            var needsUpdate = false

            if (hasNewBrightnessData.getAndSet(false) || currentBrightness > 0) {
                val intensity = cachedIntensity
                val clamped = intensity.coerceIn(0f, 1f)
                val rising = clamped > smoothedIntensity
                val f = if (rising) riseLerpFactor() else fallLerpFactor()
                smoothedIntensity = lerpFloat(smoothedIntensity, clamped, f)
                val mapped = mapIntensity(smoothedIntensity)
                val target = (targetBrightness * mapped).roundToInt()
                currentBrightness = lerpInt(currentBrightness, target, brightnessLerpFactor())
                needsUpdate = true
            }

            if (needsUpdate) {
                applyLeds()
            }

            if (isRunning.get()) {
                animationHandler?.postDelayed(this, updateInterval)
            }
        }
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setLerpStrength(strength: Float) {
        response = strength.coerceIn(0f, 1f)
    }

    override fun setSpeed(speed: Float) {
        response = speed.coerceIn(0f, 1f)
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0f, 1f)
    }

    override fun start() {
        if (isRunning.getAndSet(true)) return

        animationThread = HandlerThread("AudioReactiveAnimation").apply { start() }
        animationHandler = Handler(animationThread!!.looper)
        animationHandler?.post(ledUpdateRunnable)

        audioAnalyzer = AudioAnalyzer(mediaProjection, profile) { intensity ->
            cachedIntensity = intensity
            hasNewBrightnessData.set(true)
        }
        audioAnalyzer?.start()
    }

    override fun stop() {
        if (!isRunning.getAndSet(false)) return

        animationHandler?.removeCallbacks(ledUpdateRunnable)
        audioAnalyzer?.stop()
        audioAnalyzer = null
        hasNewBrightnessData.set(false)

        animationThread?.quitSafely()
        animationThread = null
        animationHandler = null
        currentBrightness = 0
        applyLeds()
    }

    private fun riseLerpFactor(): Float {
        val min = 0.2f
        val max = 0.9f
        return min + (max - min) * response
    }

    private fun fallLerpFactor(): Float {
        val min = 0.07f
        val max = 0.7f
        return min + (max - min) * response
    }

    private fun brightnessLerpFactor(): Float {
        val min = 0.25f
        val max = 1f
        return min + (max - min) * response
    }

    private fun mapIntensity(raw: Float): Float {
        val noiseFloor = 0.05f + (0.25f * (1f - sensitivity))
        if (raw <= noiseFloor) return 0f
        val norm = ((raw - noiseFloor) / (1f - noiseFloor)).coerceIn(0f, 1f)
        val amp = 0.5f + 1.5f * sensitivity
        val boosted = norm * amp
        return boosted.coerceIn(0f, 1f)
    }

    private fun applyLeds() {
        val scale = (currentBrightness / 255f).let {
            if (it < 0.02f) 0f else it * it
        }
        val red = (Color.red(baseColor) * scale).roundToInt().coerceIn(0, 255)
        val green = (Color.green(baseColor) * scale).roundToInt().coerceIn(0, 255)
        val blue = (Color.blue(baseColor) * scale).roundToInt().coerceIn(0, 255)

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
