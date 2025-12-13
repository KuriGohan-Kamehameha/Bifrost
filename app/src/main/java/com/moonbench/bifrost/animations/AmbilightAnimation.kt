package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
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
    private var response: Float = 0.5f
    private var saturationBoost: Float = 0.0f

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setLerpStrength(strength: Float) {
        response = strength.coerceIn(0f, 1f)
    }

    override fun setSpeed(speed: Float) {
        response = speed.coerceIn(0f, 1f)
    }

    override fun setSaturationBoost(boost: Float) {
        saturationBoost = boost.coerceIn(0f, 1f)
    }

    override fun start() {
        screenAnalyzer = ScreenAnalyzer(
            mediaProjection,
            displayMetrics,
            regionType,
            profile
        ) { colors ->
            updateColors(colors)
        }
        screenAnalyzer?.start()
    }

    override fun stop() {
        screenAnalyzer?.stop()
        screenAnalyzer = null
    }

    private fun colorLerpFactor(): Float {
        val min = 0.1f
        val max = 0.9f
        return min + (max - min) * response
    }

    private fun brightnessLerpFactor(): Float {
        val min = 0.1f
        val max = 0.9f
        return min + (max - min) * response
    }

    private fun updateColors(colors: ScreenColors) {
        performUpdate(colors)
    }

    private fun performUpdate(colors: ScreenColors) {
        when (regionType) {
            ScreenRegionType.TWO_SIDES -> updateTwoSides(colors)
            ScreenRegionType.FOUR_CORNERS -> updateFourCorners(colors)
        }
    }

    private fun updateTwoSides(colors: ScreenColors) {
        val leftTarget = if (isColorBlack(colors.leftColor)) {
            Color.BLACK
        } else {
            boostSaturation(colors.leftColor, saturationBoost)
        }

        val rightTarget = if (isColorBlack(colors.rightColor)) {
            Color.BLACK
        } else {
            boostSaturation(colors.rightColor, saturationBoost)
        }

        currentLeftColor = if (isColorBlack(leftTarget)) {
            Color.BLACK
        } else {
            lerpColor(currentLeftColor, leftTarget, colorLerpFactor())
        }

        currentRightColor = if (isColorBlack(rightTarget)) {
            Color.BLACK
        } else {
            lerpColor(currentRightColor, rightTarget, colorLerpFactor())
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
        val topLeftTarget = if (isColorBlack(colors.topLeftColor)) {
            Color.BLACK
        } else {
            boostSaturation(colors.topLeftColor, saturationBoost)
        }

        val topRightTarget = if (isColorBlack(colors.topRightColor)) {
            Color.BLACK
        } else {
            boostSaturation(colors.topRightColor, saturationBoost)
        }

        val bottomLeftTarget = if (isColorBlack(colors.bottomLeftColor)) {
            Color.BLACK
        } else {
            boostSaturation(colors.bottomLeftColor, saturationBoost)
        }

        val bottomRightTarget = if (isColorBlack(colors.bottomRightColor)) {
            Color.BLACK
        } else {
            boostSaturation(colors.bottomRightColor, saturationBoost)
        }

        currentTopLeftColor = if (isColorBlack(topLeftTarget)) {
            Color.BLACK
        } else {
            lerpColor(currentTopLeftColor, topLeftTarget, colorLerpFactor())
        }

        currentTopRightColor = if (isColorBlack(topRightTarget)) {
            Color.BLACK
        } else {
            lerpColor(currentTopRightColor, topRightTarget, colorLerpFactor())
        }

        currentBottomLeftColor = if (isColorBlack(bottomLeftTarget)) {
            Color.BLACK
        } else {
            lerpColor(currentBottomLeftColor, bottomLeftTarget, colorLerpFactor())
        }

        currentBottomRightColor = if (isColorBlack(bottomRightTarget)) {
            Color.BLACK
        } else {
            lerpColor(currentBottomRightColor, bottomRightTarget, colorLerpFactor())
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