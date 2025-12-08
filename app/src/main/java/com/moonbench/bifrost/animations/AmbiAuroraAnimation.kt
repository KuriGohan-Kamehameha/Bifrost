package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import com.moonbench.bifrost.tools.AudioAnalyzer
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.tools.ScreenAnalyzer
import com.moonbench.bifrost.tools.ScreenColors
import com.moonbench.bifrost.tools.ScreenRegionType
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class AmbiAuroraAnimation(
    ledController: LedController,
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val regionType: ScreenRegionType,
    private val profile: PerformanceProfile
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.AMBIAURORA
    override val needsColorSelection: Boolean = false

    private var screenAnalyzer: ScreenAnalyzer? = null
    private var audioAnalyzer: AudioAnalyzer? = null

    private var currentLeftColor = Color.BLACK
    private var currentRightColor = Color.BLACK
    private var currentTopLeftColor = Color.BLACK
    private var currentTopRightColor = Color.BLACK
    private var currentBottomLeftColor = Color.BLACK
    private var currentBottomRightColor = Color.BLACK

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 0
    private var lerpStrength: Float = 0.5f
    private var speed: Float = 0.5f
    private var sensitivity: Float = 0.5f
    private var smoothedIntensity: Float = 0f

    private val hasNewColorData = AtomicBoolean(false)
    private val hasNewBrightnessData = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)

    @Volatile private var cachedColors: ScreenColors? = null
    @Volatile private var cachedIntensity: Float = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval: Long
        get() = if (profile.intervalMs == 0L) 16L else profile.intervalMs

    private val ledUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return

            var needsUpdate = false

            if (hasNewColorData.getAndSet(false)) {
                cachedColors?.let { colors ->
                    when (regionType) {
                        ScreenRegionType.TWO_SIDES -> updateTwoSidesColors(colors)
                        ScreenRegionType.FOUR_CORNERS -> updateFourCornersColors(colors)
                    }
                    needsUpdate = true
                }
            }

            if (hasNewBrightnessData.getAndSet(false)) {
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

        screenAnalyzer = ScreenAnalyzer(
            mediaProjection,
            displayMetrics,
            regionType
        ) { colors ->
            cachedColors = colors
            hasNewColorData.set(true)
        }
        screenAnalyzer?.start()

        audioAnalyzer = AudioAnalyzer(mediaProjection) { intensity ->
            cachedIntensity = intensity
            hasNewBrightnessData.set(true)
        }
        audioAnalyzer?.start()
    }

    override fun stop() {
        isRunning.set(false)
        handler.removeCallbacks(ledUpdateRunnable)
        screenAnalyzer?.stop()
        screenAnalyzer = null
        audioAnalyzer?.stop()
        audioAnalyzer = null
        cachedColors = null
        hasNewColorData.set(false)
        hasNewBrightnessData.set(false)
    }

    private fun colorLerpFactor(): Float {
        return 0.1f + 0.8f * lerpStrength
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

    private fun updateTwoSidesColors(colors: ScreenColors) {
        currentLeftColor = if (isColorBlack(colors.leftColor)) {
            Color.BLACK
        } else {
            lerpColor(currentLeftColor, colors.leftColor, colorLerpFactor())
        }

        currentRightColor = if (isColorBlack(colors.rightColor)) {
            Color.BLACK
        } else {
            lerpColor(currentRightColor, colors.rightColor, colorLerpFactor())
        }
    }

    private fun updateFourCornersColors(colors: ScreenColors) {
        currentTopLeftColor = if (isColorBlack(colors.topLeftColor)) {
            Color.BLACK
        } else {
            lerpColor(currentTopLeftColor, colors.topLeftColor, colorLerpFactor())
        }

        currentTopRightColor = if (isColorBlack(colors.topRightColor)) {
            Color.BLACK
        } else {
            lerpColor(currentTopRightColor, colors.topRightColor, colorLerpFactor())
        }

        currentBottomLeftColor = if (isColorBlack(colors.bottomLeftColor)) {
            Color.BLACK
        } else {
            lerpColor(currentBottomLeftColor, colors.bottomLeftColor, colorLerpFactor())
        }

        currentBottomRightColor = if (isColorBlack(colors.bottomRightColor)) {
            Color.BLACK
        } else {
            lerpColor(currentBottomRightColor, colors.bottomRightColor, colorLerpFactor())
        }
    }

    private fun applyLeds() {
        val scale = currentBrightness / 255f
        when (regionType) {
            ScreenRegionType.TWO_SIDES -> applyTwoSides(scale)
            ScreenRegionType.FOUR_CORNERS -> applyFourCorners(scale)
        }
    }

    private fun applyTwoSides(scale: Float) {
        val leftRed = (Color.red(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val leftGreen = (Color.green(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val leftBlue = (Color.blue(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)

        val rightRed = (Color.red(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
        val rightGreen = (Color.green(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
        val rightBlue = (Color.blue(currentRightColor) * scale).roundToInt().coerceIn(0, 255)

        ledController.setLedColor(
            leftRed,
            leftGreen,
            leftBlue,
            leftTop = true,
            leftBottom = true,
            rightTop = false,
            rightBottom = false
        )

        ledController.setLedColor(
            rightRed,
            rightGreen,
            rightBlue,
            leftTop = false,
            leftBottom = false,
            rightTop = true,
            rightBottom = true
        )
    }

    private fun applyFourCorners(scale: Float) {
        val tlR = (Color.red(currentTopLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val tlG = (Color.green(currentTopLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val tlB = (Color.blue(currentTopLeftColor) * scale).roundToInt().coerceIn(0, 255)

        val trR = (Color.red(currentTopRightColor) * scale).roundToInt().coerceIn(0, 255)
        val trG = (Color.green(currentTopRightColor) * scale).roundToInt().coerceIn(0, 255)
        val trB = (Color.blue(currentTopRightColor) * scale).roundToInt().coerceIn(0, 255)

        val blR = (Color.red(currentBottomLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val blG = (Color.green(currentBottomLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val blB = (Color.blue(currentBottomLeftColor) * scale).roundToInt().coerceIn(0, 255)

        val brR = (Color.red(currentBottomRightColor) * scale).roundToInt().coerceIn(0, 255)
        val brG = (Color.green(currentBottomRightColor) * scale).roundToInt().coerceIn(0, 255)
        val brB = (Color.blue(currentBottomRightColor) * scale).roundToInt().coerceIn(0, 255)

        ledController.setLedColor(
            tlR,
            tlG,
            tlB,
            leftTop = true,
            leftBottom = false,
            rightTop = false,
            rightBottom = false
        )

        ledController.setLedColor(
            trR,
            trG,
            trB,
            leftTop = false,
            leftBottom = false,
            rightTop = true,
            rightBottom = false
        )

        ledController.setLedColor(
            blR,
            blG,
            blB,
            leftTop = false,
            leftBottom = true,
            rightTop = false,
            rightBottom = false
        )

        ledController.setLedColor(
            brR,
            brG,
            brB,
            leftTop = false,
            leftBottom = false,
            rightTop = false,
            rightBottom = true
        )
    }
}