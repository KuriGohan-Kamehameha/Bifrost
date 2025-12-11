package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import com.moonbench.bifrost.tools.AudioAnalyzer
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.tools.ScreenAnalyzer
import com.moonbench.bifrost.tools.ScreenColors
import com.moonbench.bifrost.tools.ScreenRegionType
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
    private var response: Float = 0.5f
    private var sensitivity: Float = 0.5f
    private var smoothedIntensity: Float = 0f

    @Volatile
    private var isRunning = false

    private var updateThread: HandlerThread? = null
    private var updateHandler: Handler? = null

    private var hasColorUpdate = false
    private var hasAudioUpdate = false
    private var pendingColors: ScreenColors? = null
    private var pendingIntensity: Float = 0f

    private val updateInterval: Long
        get() = if (profile.intervalMs == 0L) 16L else profile.intervalMs

    private val combinedUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            var needsLedUpdate = false

            if (hasColorUpdate) {
                hasColorUpdate = false
                pendingColors?.let { colors ->
                    when (regionType) {
                        ScreenRegionType.TWO_SIDES -> updateTwoSidesColors(colors)
                        ScreenRegionType.FOUR_CORNERS -> updateFourCornersColors(colors)
                    }
                    needsLedUpdate = true
                }
            }

            if (hasAudioUpdate) {
                hasAudioUpdate = false
                val intensity = pendingIntensity.coerceIn(0f, 1f)
                val rising = intensity > smoothedIntensity
                val f = if (rising) riseLerpFactor() else fallLerpFactor()
                smoothedIntensity = lerpFloat(smoothedIntensity, intensity, f)
                val mapped = mapIntensity(smoothedIntensity)
                val target = (targetBrightness * mapped).roundToInt()
                currentBrightness = lerpInt(currentBrightness, target, brightnessLerpFactor())
                needsLedUpdate = true
            }

            if (needsLedUpdate) {
                applyLeds()
            }

            if (isRunning) {
                updateHandler?.postDelayed(this, updateInterval)
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
        if (isRunning) return
        isRunning = true

        updateThread = HandlerThread("AmbiAuroraUpdate").apply {
            start()
            priority = Thread.MAX_PRIORITY
        }
        updateHandler = Handler(updateThread!!.looper)

        updateHandler?.post(combinedUpdateRunnable)

        screenAnalyzer = ScreenAnalyzer(
            mediaProjection,
            displayMetrics,
            regionType,
            profile
        ) { colors ->
            pendingColors = colors
            hasColorUpdate = true
        }
        screenAnalyzer?.start()

        audioAnalyzer = AudioAnalyzer(mediaProjection, profile) { intensity ->
            pendingIntensity = intensity
            hasAudioUpdate = true
        }
        audioAnalyzer?.start()
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false

        updateHandler?.removeCallbacks(combinedUpdateRunnable)
        screenAnalyzer?.stop()
        screenAnalyzer = null
        audioAnalyzer?.stop()
        audioAnalyzer = null

        updateThread?.quitSafely()
        updateThread = null
        updateHandler = null

        currentBrightness = 0
        applyLeds()
    }

    private fun colorLerpFactor(): Float {
        val min = 0.1f
        val max = 0.9f
        return min + (max - min) * response
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