package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.tools.ScreenAnalyzer
import com.moonbench.bifrost.tools.ScreenColors
import com.moonbench.bifrost.tools.ScreenRegionType
import kotlin.math.roundToInt

class AmbilightAnimation(
    ledController: LedController,
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val regionType: ScreenRegionType,
    private val profile: PerformanceProfile
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.AMBILIGHT
    override val needsColorSelection: Boolean = false

    private var screenAnalyzer: ScreenAnalyzer? = null

    private var currentLeftColor = Color.BLACK
    private var currentRightColor = Color.BLACK
    private var currentTopLeftColor = Color.BLACK
    private var currentTopRightColor = Color.BLACK
    private var currentBottomLeftColor = Color.BLACK
    private var currentBottomRightColor = Color.BLACK

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 255
    private var lerpStrength: Float = 0.5f

    private var lastUpdateTime = 0L
    private val minUpdateInterval = profile.intervalMs
    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate: Runnable? = null

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setLerpStrength(strength: Float) {
        lerpStrength = strength.coerceIn(0f, 1f)
    }

    override fun start() {
        screenAnalyzer = ScreenAnalyzer(
            mediaProjection,
            displayMetrics,
            regionType
        ) { colors ->
            updateColors(colors)
        }
        screenAnalyzer?.start()
    }

    override fun stop() {
        pendingUpdate?.let { handler.removeCallbacks(it) }
        pendingUpdate = null
        screenAnalyzer?.stop()
        screenAnalyzer = null
    }

    private fun colorLerpFactor(): Float {
        return 0.1f + 0.8f * lerpStrength
    }

    private fun brightnessLerpFactor(): Float {
        return 0.1f + 0.8f * lerpStrength
    }

    private fun updateColors(colors: ScreenColors) {
        if (minUpdateInterval == 0L) {
            performUpdate(colors)
            return
        }

        val currentTime = System.currentTimeMillis()

        if (currentTime - lastUpdateTime < minUpdateInterval) {
            if (pendingUpdate == null) {
                pendingUpdate = Runnable {
                    pendingUpdate = null
                    lastUpdateTime = System.currentTimeMillis()
                    performUpdate(colors)
                }
                handler.postDelayed(pendingUpdate!!, minUpdateInterval)
            }
            return
        }

        lastUpdateTime = currentTime
        performUpdate(colors)
    }

    private fun performUpdate(colors: ScreenColors) {
        when (regionType) {
            ScreenRegionType.TWO_SIDES -> updateTwoSides(colors)
            ScreenRegionType.FOUR_CORNERS -> updateFourCorners(colors)
        }
    }

    private fun updateTwoSides(colors: ScreenColors) {
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

        currentBrightness = lerpInt(currentBrightness, targetBrightness, brightnessLerpFactor())

        val scale = currentBrightness / 255f

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

    private fun updateFourCorners(colors: ScreenColors) {
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

        currentBrightness = lerpInt(currentBrightness, targetBrightness, brightnessLerpFactor())

        val scale = currentBrightness / 255f

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