package com.moonbench.bifrost.external

import com.moonbench.bifrost.animations.LedAnimationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which effects need capture and when Bifrost can start them for an
 * external caller. A wrong "available" starts an override that stays dark.
 */
class ExternalCaptureTest {

    @Test fun onlyTheThreeCaptureEffectsAreReactive() {
        val reactive = LedAnimationType.values().filter { ExternalCapture.isReactive(it) }.toSet()
        assertEquals(
            setOf(LedAnimationType.AMBIENT, LedAnimationType.AUDIO_REACTIVE, LedAnimationType.AMBIAURORA),
            reactive
        )
    }

    // isAvailable(effect, hasProjection, audioPermitted, accessibilityRunning, ambientPrefersProjection)

    @Test fun audioEffectsNeedProjectionAndRecordAudio() {
        for (e in listOf(LedAnimationType.AUDIO_REACTIVE, LedAnimationType.AMBIAURORA)) {
            assertFalse(ExternalCapture.isAvailable(e, false, true, true, true))   // no projection
            assertFalse(ExternalCapture.isAvailable(e, true, false, true, true))   // no RECORD_AUDIO
            assertTrue(ExternalCapture.isAvailable(e, true, true, false, false))
        }
    }

    @Test fun ambientFollowsTheUsersCapturePreference() {
        val a = LedAnimationType.AMBIENT
        assertTrue(ExternalCapture.isAvailable(a, false, false, true, false))   // accessibility chosen and running
        assertTrue(ExternalCapture.isAvailable(a, true, false, false, true))    // projection preferred + held; no audio needed
        assertFalse(ExternalCapture.isAvailable(a, true, true, false, false))   // projection held but user chose accessibility, which is off
        assertFalse(ExternalCapture.isAvailable(a, false, true, true, true))    // projection preferred but missing: no accessibility fallback
    }

    @Test fun plainEffectsAlwaysAvailable() {
        assertTrue(ExternalCapture.isAvailable(LedAnimationType.PIPBOY, false, false, false, false))
        assertTrue(ExternalCapture.isAvailable(LedAnimationType.STATIC, false, false, false, false))
    }
}
