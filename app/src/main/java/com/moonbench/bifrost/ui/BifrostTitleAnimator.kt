package com.moonbench.bifrost.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Encapsulates the colourful rainbow/watercolor title text animation
 * and the logo spin-pulse-drift animation for the Bifrost header.
 *
 * Extracted so the effect survives upstream merges that strip it out.
 */
class BifrostTitleAnimator(
    private val titleText: TextView,
    private val logoView: ImageView
) {

    companion object {
        private const val TITLE_INTRO_ANIMATION_MS = 3200L
    }

    private var titleLabel: String = ""
    private var titleIntroAnimator: ValueAnimator? = null
    private var headerSettleAnimator: ValueAnimator? = null

    /** Call once after views are ready (replaces setupRainbowTitleText). */
    fun setup() {
        titleLabel = titleText.text.toString()
        if (titleLabel.isBlank()) return

        logoView.setOnClickListener { play() }
        titleText.setOnClickListener { play() }
        resetToWatercolor()
        play()
    }

    /** Trigger the full rainbow intro animation. */
    fun play() {
        if (titleLabel.isBlank()) return

        titleIntroAnimator?.cancel()
        headerSettleAnimator?.cancel()

        titleIntroAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TITLE_INTRO_ANIMATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val phaseDegrees = progress * 720f
                applyRainbowPhase(titleLabel, phaseDegrees)
                applyLogoFrame(progress, phaseDegrees)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var wasCanceled = false

                override fun onAnimationCancel(animation: Animator) {
                    wasCanceled = true
                    settleToStill()
                }

                override fun onAnimationEnd(animation: Animator) {
                    titleIntroAnimator = null
                    if (!wasCanceled) {
                        settleToStill()
                    }
                }
            })
            start()
        }
    }

    /** Cancel running animations and release references. */
    fun teardown() {
        titleIntroAnimator?.cancel()
        titleIntroAnimator = null
        headerSettleAnimator?.cancel()
        headerSettleAnimator = null
    }

    // ── rainbow title ───────────────────────────────────────────────

    private fun applyRainbowPhase(text: String, phaseDegrees: Float) {
        val spannable = SpannableString(text)
        val maxIndex = (text.length - 1).coerceAtLeast(1)

        text.indices.forEach { index ->
            if (text[index].isWhitespace()) return@forEach
            val hue = (phaseDegrees + (360f * index / maxIndex)) % 360f
            val color = Color.HSVToColor(floatArrayOf(hue, 0.82f, 1f))
            spannable.setSpan(
                ForegroundColorSpan(color),
                index, index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        titleText.text = spannable
    }

    // ── watercolor title ────────────────────────────────────────────

    private fun applyWatercolorPhase(text: String, phaseDegrees: Float) {
        val spannable = SpannableString(text)
        val maxIndex = (text.length - 1).coerceAtLeast(1)

        text.indices.forEach { index ->
            if (text[index].isWhitespace()) return@forEach
            val lp = index / maxIndex.toFloat()
            val hue = (phaseDegrees + 300f * lp + 10f * sin(lp * PI).toFloat()) % 360f
            val saturation = (0.28f + 0.14f * ((sin(lp * PI * 3.0) + 1.0) / 2.0).toFloat())
                .coerceIn(0f, 1f)
            val value = (0.92f + 0.08f * ((cos(lp * PI * 2.0) + 1.0) / 2.0).toFloat())
                .coerceIn(0f, 1f)
            val alpha = (224 + 31 * ((sin(lp * PI * 2.5) + 1.0) / 2.0)).roundToInt()
                .coerceIn(0, 255)
            val color = Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value))
            spannable.setSpan(
                ForegroundColorSpan(color),
                index, index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        titleText.text = spannable
    }

    // ── logo frame ──────────────────────────────────────────────────

    private fun applyLogoFrame(progress: Float, @Suppress("UNUSED_PARAMETER") phaseDegrees: Float) {
        val spinWave = sin(progress * PI * 10.0).toFloat()
        val pulseWave = ((1.0 - cos(progress * PI * 6.0)) / 2.0).toFloat()
        val driftWave = sin(progress * PI * 4.0).toFloat()

        logoView.rotation = 16f * spinWave
        logoView.scaleX = 1f + (0.12f * pulseWave)
        logoView.scaleY = 1f + (0.12f * pulseWave)
        logoView.translationY = -10f * driftWave
        logoView.alpha = 0.9f + (0.1f * pulseWave)
    }

    // ── reset / settle ──────────────────────────────────────────────

    private fun resetToWatercolor() {
        if (titleLabel.isBlank()) return
        applyWatercolorPhase(titleLabel, 18f)
        logoView.rotation = 0f
        logoView.scaleX = 1f
        logoView.scaleY = 1f
        logoView.translationY = 0f
        logoView.alpha = 1f
        logoView.clearColorFilter()
    }

    private fun settleToStill() {
        if (titleLabel.isBlank()) return
        headerSettleAnimator?.cancel()

        logoView.animate()
            .rotation(0f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(400L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { logoView.clearColorFilter() }
            .start()

        val rainbowColors = buildColorArray(titleLabel, 720f, rainbow = true)
        val watercolorColors = buildColorArray(titleLabel, 18f, rainbow = false)

        headerSettleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                applyBlendedColors(rainbowColors, watercolorColors, t)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    applyWatercolorPhase(titleLabel, 18f)
                }
            })
            start()
        }
    }

    // ── colour helpers ──────────────────────────────────────────────

    private fun buildColorArray(text: String, phaseDegrees: Float, rainbow: Boolean): IntArray {
        val colors = IntArray(text.length)
        val maxIndex = (text.length - 1).coerceAtLeast(1)
        text.indices.forEach { index ->
            if (text[index].isWhitespace()) {
                colors[index] = Color.TRANSPARENT
            } else if (rainbow) {
                val hue = (phaseDegrees + (360f * index / maxIndex)) % 360f
                colors[index] = Color.HSVToColor(floatArrayOf(hue, 0.82f, 1f))
            } else {
                val lp = index / maxIndex.toFloat()
                val hue = (phaseDegrees + 300f * lp + 10f * sin(lp * PI).toFloat()) % 360f
                val saturation = (0.28f + 0.14f * ((sin(lp * PI * 3.0) + 1.0) / 2.0).toFloat())
                    .coerceIn(0f, 1f)
                val value = (0.92f + 0.08f * ((cos(lp * PI * 2.0) + 1.0) / 2.0).toFloat())
                    .coerceIn(0f, 1f)
                val alpha = (224 + 31 * ((sin(lp * PI * 2.5) + 1.0) / 2.0)).roundToInt()
                    .coerceIn(0, 255)
                colors[index] = Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value))
            }
        }
        return colors
    }

    private fun applyBlendedColors(startColors: IntArray, endColors: IntArray, t: Float) {
        val spannable = SpannableString(titleLabel)
        val blend = t.coerceIn(0f, 1f)

        titleLabel.indices.forEach { index ->
            if (titleLabel[index].isWhitespace()) return@forEach
            val blended = ColorUtils.blendARGB(
                startColors.getOrNull(index) ?: Color.WHITE,
                endColors.getOrNull(index) ?: Color.WHITE,
                blend
            )
            spannable.setSpan(
                ForegroundColorSpan(blended),
                index, index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        titleText.text = spannable
    }
}
