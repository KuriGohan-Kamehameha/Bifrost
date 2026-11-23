package com.moonbench.bifrost.external

import com.moonbench.bifrost.external.ExternalApiGate.Power
import com.moonbench.bifrost.plugins.ExternalGrant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The v2 permission rule: global switch, or a plugin grant for a verified caller. */
class ExternalApiGateTest {

    private val grant = ExternalGrant(presets = setOf("Aurora"), effects = setOf("AMBIENT"))

    @Test fun offByDefaultWithoutGrant() {
        assertFalse(ExternalApiGate.allowsPower(Power.Preset("Aurora"), false, true, null))
        assertFalse(ExternalApiGate.allowsPower(Power.Effect("AMBIENT"), false, false, null))
    }

    @Test fun globalSwitchAllowsEveryone() {
        assertTrue(ExternalApiGate.allowsPower(Power.Preset("Anything"), true, false, null))
        assertTrue(ExternalApiGate.allowsPower(Power.Effect("AUDIO_REACTIVE"), true, false, null))
    }

    @Test fun grantNeedsAVerifiedCaller() {
        assertFalse(ExternalApiGate.allowsPower(Power.Preset("Aurora"), false, false, grant))
        assertTrue(ExternalApiGate.allowsPower(Power.Preset("Aurora"), false, true, grant))
    }

    @Test fun grantIsScoped() {
        assertFalse(ExternalApiGate.allowsPower(Power.Preset("Other"), false, true, grant))
        assertTrue(ExternalApiGate.allowsPower(Power.Effect("AMBIENT"), false, true, grant))
        assertFalse(ExternalApiGate.allowsPower(Power.Effect("AUDIO_REACTIVE"), false, true, grant))
    }

    @Test fun wildcardGrant() {
        val any = ExternalGrant(presets = setOf("*"), effects = setOf("*"))
        assertTrue(ExternalApiGate.allowsPower(Power.Preset("Whatever"), false, true, any))
        assertTrue(ExternalApiGate.allowsPower(Power.Effect("AMBIAURORA"), false, true, any))
    }
}
