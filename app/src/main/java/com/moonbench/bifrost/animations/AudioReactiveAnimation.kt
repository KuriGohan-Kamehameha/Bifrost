package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
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
    private var lerpStrength: Float = 0.5f
    private var speed: Float = 0.5f
    private var sensitivity: Float = 0.5f
    private var smoothedIntensity: Float = 0f

    private val hasNewData = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)

    @Volatile private var cachedIntensity: Float = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval: Long
        get() = if (profile.intervalMs == 0L) 16L else profile.intervalMs

    private val ledUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return

            if (hasNewData.getAndSet(false)) {
                val intensity = cachedIntensity
                val clamped = intensity.coerceIn(0f, 1f)
                val rising = clamped > smoothedIntensity
                val f = if (rising) riseLerpFactor() else fallLerpFactor()
                smoothedIntensity = lerpFloat(smoothedIntensity, clamped, f)
                val mapped = mapIntensity(smoothedIntensity)
                val target = (targetBrightness * mapped).roundToInt()
                currentBrightness = lerpInt(currentBrightness, target, brightnessLerpFactor())
                applyLeds()
            }

            if (isRunning.get()) {
                handler.postDelayed(this, updateInterval)
            }
        }
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setLerpStrength(strength: Float) {
        lerpStrength = strength.coerceIn(0f, 1f)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0f, 1f)
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0f, 1f)
    }

    override fun start() {
        if (isRunning.getAndSet(true)) return

        handler.post(ledUpdateRunnable)

        audioAnalyzer = AudioAnalyzer(mediaProjection) { intensity ->
            cachedIntensity = intensity
            hasNewData.set(true)
        }
        audioAnalyzer?.start()
    }

    override fun stop() {
        isRunning.set(false)
        handler.removeCallbacks(ledUpdateRunnable)
        audioAnalyzer?.stop()
        audioAnalyzer = null
        hasNewData.set(false)
    }

    private fun riseLerpFactor(): Float {
        val fastBase = 0.4f + 0.5f * speed
        val smoothMul = 0.3f + 0.7f * (1f - lerpStrength)
        return (fastBase * smoothMul).coerceIn(0.3f, 0.95f)
    }

    private fun fallLerpFactor(): Float {
        val slowBase = 0.1f + 0.4f * speed
        val smoothMul = 0.5f + 0.5f * (1f - lerpStrength)
        return (slowBase * smoothMul).coerceIn(0.05f, 0.6f)
    }

    private fun brightnessLerpFactor(): Float {
        val base = 0.3f + 0.6f * speed
        val smoothMul = 0.2f + 0.8f * (1f - lerpStrength)
        return (base * smoothMul).coerceIn(0.3f, 1f)
    }

    private fun mapIntensity(raw: Float): Float {
        val noiseFloor = 0.05f + (0.25f * (1f - sensitivity))
        val norm = ((raw - noiseFloor) / (1f - noiseFloor)).coerceIn(0f, 1f)
        val baseAmp = 1.3f + 0.7f * (1f - lerpStrength)
        val sensitivityAmp = 0.5f + (1.5f * sensitivity)
        val speedAmp = 0.7f + 0.6f * speed
        val boosted = norm * baseAmp * sensitivityAmp * speedAmp
        return boosted.coerceIn(0f, 1f)
    }

    private fun applyLeds() {
        val scale = currentBrightness / 255f
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
