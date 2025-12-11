package com.moonbench.bifrost.animations

import android.graphics.Color
import com.moonbench.bifrost.tools.LedController
import kotlin.math.abs
import kotlin.math.roundToInt

abstract class LedAnimation(protected val ledController: LedController) {

    abstract val type: LedAnimationType
    abstract val needsColorSelection: Boolean

    abstract fun start()
    abstract fun stop()

    open fun setTargetColor(color: Int) {}
    open fun setTargetBrightness(brightness: Int) {}
    open fun setLerpStrength(strength: Float) {}
    open fun setSpeed(speed: Float) {}
    open fun setSensitivity(sensitivity: Float) {}

    protected fun lerpInt(from: Int, to: Int, factor: Float): Int {
        if (from == to) return from.coerceIn(0, 255)
        val f = factor.coerceIn(0f, 1f)
        val raw = from + (to - from) * f
        val result = if (abs(to - raw) < 1f) to else raw.roundToInt()
        return result.coerceIn(0, 255)
    }

    protected fun lerpFloat(from: Float, to: Float, factor: Float): Float {
        val f = factor.coerceIn(0f, 1f)
        return from + (to - from) * f
    }

    protected fun lerpColor(from: Int, to: Int, factor: Float): Int {
        val r = lerpInt(Color.red(from), Color.red(to), factor)
        val g = lerpInt(Color.green(from), Color.green(to), factor)
        val b = lerpInt(Color.blue(from), Color.blue(to), factor)
        return Color.rgb(r, g, b)
    }

    protected fun isColorBlack(color: Int): Boolean {
        return Color.red(color) == 0 && Color.green(color) == 0 && Color.blue(color) == 0
    }
}
