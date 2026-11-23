package com.moonbench.bifrost.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the external IPC wire contract.
 *
 * Strip-Boy (the Fallout 4 Pip-Boy patch) is a separate APK that hardcodes
 * these exact action/extra strings in smali to drive the LEDs. Renaming a
 * constant here compiles fine but SILENTLY breaks the integration (the menu
 * pulse / channel-swap static, the colour/flicker dispatch). Pin the values.
 */
class ExternalApiContractTest {

    @Test
    fun action_pulse_contract_is_stable() {
        assertEquals("com.moonbench.bifrost.api.ACTION_PULSE", ExternalApi.ACTION_PULSE)
        assertEquals("pulseKind", ExternalApi.EXTRA_PULSE_KIND)
        assertEquals("PULSE", ExternalApi.PULSE_KIND_PULSE)
        assertEquals("STATIC", ExternalApi.PULSE_KIND_STATIC)
    }

    @Test
    fun display_contract_is_stable() {
        assertEquals("com.moonbench.bifrost.api.ACTION_DISPLAY", ExternalApi.ACTION_DISPLAY)
        assertEquals("phaseSeconds", ExternalApi.EXTRA_PHASE_SECONDS)
        assertEquals("com.moonbench.bifrost.permission.CONTROL_LEDS", ExternalApi.PERMISSION)
    }

    /** v1 callers (Strip-Boy and other apps) keep working against a v2 Bifrost. */
    @Test
    fun v1_is_still_accepted_and_v2_is_current() {
        assertEquals(2, ExternalApi.API_VERSION)
        assertEquals(1, ExternalApi.MIN_API_VERSION)
        assertTrue(ExternalCommandValidator.isSupportedVersion(1))
        assertTrue(ExternalCommandValidator.isSupportedVersion(2))
        assertFalse(ExternalCommandValidator.isSupportedVersion(0))
        assertFalse(ExternalCommandValidator.isSupportedVersion(3))
    }

    /** v1 result codes are part of callers' control flow (e.g. -3 = "no PIPBOY here"). */
    @Test
    fun v1_result_codes_are_stable() {
        assertEquals(0, ExternalApi.RESULT_ACCEPTED)
        assertEquals(-1, ExternalApi.RESULT_REJECTED_DISABLED)
        assertEquals(-2, ExternalApi.RESULT_REJECTED_VERSION)
        assertEquals(-3, ExternalApi.RESULT_REJECTED_VALIDATION)
        assertEquals(-4, ExternalApi.RESULT_REJECTED_UNAUTHORIZED)
        assertEquals(-5, ExternalApi.RESULT_REJECTED_RATE_LIMITED)
        assertEquals(-6, ExternalApi.RESULT_REJECTED_UNKNOWN_ACTION)
        assertEquals(1, ExternalApi.RESULT_NOT_FOUND)
    }

    @Test
    fun v2_contract_is_stable() {
        assertEquals("com.moonbench.bifrost.api.ACTION_APPLY_PRESET", ExternalApi.ACTION_APPLY_PRESET)
        assertEquals("com.moonbench.bifrost.api.ACTION_INVOKE", ExternalApi.ACTION_INVOKE)
        assertEquals("com.moonbench.bifrost.api.ACTION_QUERY", ExternalApi.ACTION_QUERY)
        assertEquals("callerToken", ExternalApi.EXTRA_CALLER_TOKEN)
        assertEquals("presetName", ExternalApi.EXTRA_PRESET_NAME)
        assertEquals("function", ExternalApi.EXTRA_FUNCTION)
        assertEquals("BRIGHTNESS", ExternalApi.FUNCTION_BRIGHTNESS)
        assertEquals("PULSE", ExternalApi.FUNCTION_PULSE)
        assertEquals("PAUSE", ExternalApi.FUNCTION_PAUSE)
        assertEquals("RESUME", ExternalApi.FUNCTION_RESUME)
        assertEquals(-7, ExternalApi.RESULT_REJECTED_NOT_PERMITTED)
        assertEquals(-8, ExternalApi.RESULT_REJECTED_CAPTURE_UNAVAILABLE)
        assertEquals(-9, ExternalApi.RESULT_REJECTED_SERVICE_NOT_RUNNING)
    }

    @Test
    fun every_function_round_trips_its_wire_name() {
        ExternalFunction.values().forEach {
            assertEquals(it, ExternalFunction.fromWire(it.wireName))
        }
        assertNull(ExternalFunction.fromWire("REBOOT"))
        assertNull(ExternalFunction.fromWire(null))
    }
}
