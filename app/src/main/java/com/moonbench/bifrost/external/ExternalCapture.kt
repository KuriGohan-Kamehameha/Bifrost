package com.moonbench.bifrost.external

import com.moonbench.bifrost.animations.LedAnimationType

/**
 * Which capture an effect needs, and whether Bifrost holds it right now. Pure
 * (no Android types) so the rules are unit-tested; the live inputs come from
 * LEDService (capture session / consent grant, RECORD_AUDIO) and
 * BifrostAccessibilityService (running).
 *
 * Mirrors what LEDService.createAnimation actually does:
 *  - AMBIENT samples the screen through MediaProjection when the user prefers
 *    it, and then needs a live capture session or an unconsumed consent grant
 *    (no silent accessibility fallback); otherwise it uses the accessibility
 *    screenshot service.
 *  - AUDIO_REACTIVE and AMBIAURORA need MediaProjection (playback capture) and
 *    the RECORD_AUDIO permission; without either createAnimation returns null
 *    and the LEDs stay dark.
 *  - Everything else needs nothing.
 *
 * "Projection" means LEDService can start capture without a new prompt: a live
 * session it reuses, or a single-use grant it consumes on the next capture
 * start. A grant is never replayed.
 *
 * An external caller can never obtain the capture consent itself (only
 * Bifrost's own UI can show that prompt), so a missing capture is reported to
 * the caller instead of starting a dark override.
 */
object ExternalCapture {

    enum class Need { NONE, SCREEN, PROJECTION }

    fun isReactive(effect: LedAnimationType): Boolean = needOf(effect) != Need.NONE

    fun needOf(effect: LedAnimationType): Need = when (effect) {
        LedAnimationType.AMBIENT -> Need.SCREEN
        LedAnimationType.AUDIO_REACTIVE, LedAnimationType.AMBIAURORA -> Need.PROJECTION
        else -> Need.NONE
    }

    /**
     * @param hasProjection LEDService holds a live capture session or an unconsumed consent grant.
     * @param audioPermitted RECORD_AUDIO is granted (needed by the audio-reactive effects).
     * @param accessibilityRunning the accessibility capture service is connected.
     * @param ambientPrefersProjection the user's "use MediaProjection for ambient" setting.
     */
    fun isAvailable(
        effect: LedAnimationType,
        hasProjection: Boolean,
        audioPermitted: Boolean,
        accessibilityRunning: Boolean,
        ambientPrefersProjection: Boolean,
    ): Boolean = when (needOf(effect)) {
        Need.NONE -> true
        Need.PROJECTION -> hasProjection && (!effect.supportsAudioSensitivity || audioPermitted)
        Need.SCREEN -> if (ambientPrefersProjection) hasProjection else accessibilityRunning
    }
}
