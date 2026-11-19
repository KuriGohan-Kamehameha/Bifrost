package com.moonbench.bifrost.external

import com.moonbench.bifrost.plugins.ExternalGrant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the ACTION_QUERY reply that callers parse to build their menus. */
class ExternalQueryTest {

    private val presets = """
        [
          {"name": "Aurora", "animationType": "AMBIENT"},
          {"name": "Party", "animationType": "AUDIO_REACTIVE", "ownerPackage": "plugin:x"},
          {"name": "Calm", "animationType": "BREATH"},
          {"name": "", "animationType": "STATIC"}
        ]
    """.trimIndent()

    private fun inputs(
        enabled: Boolean = true,
        verified: String? = null,
        privileged: Boolean = false,
        grant: ExternalGrant? = null,
    ) = ExternalQuery.Inputs(
        enabled = enabled,
        serviceRunning = true,
        verifiedCaller = verified,
        privilegedEnabled = privileged,
        grant = grant,
        hasProjection = false,
        audioPermitted = true,
        accessibilityRunning = true,
        ambientPrefersProjection = false,
        presetsJson = presets,
    )

    private fun byName(o: JSONObject, list: String, name: String): JSONObject {
        val arr = o.getJSONArray(list)
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).getString("name") == name) return arr.getJSONObject(i)
        throw AssertionError("$name not in $list")
    }

    @Test fun disabledRepliesOnlyVersions() {
        val o = ExternalQuery.build(inputs(enabled = false))
        assertEquals(2, o.getInt("apiVersion"))
        assertEquals(1, o.getInt("minApiVersion"))
        assertFalse(o.getBoolean("enabled"))
        assertFalse(o.has("presets"))
    }

    @Test fun listsPresetsWithCaptureState() {
        val o = ExternalQuery.build(inputs())
        assertEquals(3, o.getJSONArray("presets").length())   // blank name skipped
        assertTrue(byName(o, "presets", "Aurora").getBoolean("available"))     // accessibility
        assertFalse(byName(o, "presets", "Party").getBoolean("available"))     // no projection token
        assertEquals("plugin:x", byName(o, "presets", "Party").getString("owner"))
        assertFalse(byName(o, "presets", "Calm").getBoolean("allowed"))        // presets need permission
        assertTrue(byName(o, "effects", "STATIC").getBoolean("allowed"))       // plain effects never do
        assertFalse(byName(o, "effects", "AMBIENT").getBoolean("allowed"))
        assertEquals("PROJECTION", byName(o, "effects", "AUDIO_REACTIVE").getString("capture"))
        assertEquals(4, o.getJSONArray("functions").length())
        assertTrue(o.isNull("caller"))
    }

    @Test fun grantShowsForVerifiedCallerOnly() {
        val grant = ExternalGrant(presets = setOf("Calm"), effects = setOf("AMBIENT"))
        val verified = ExternalQuery.build(inputs(verified = "com.game", grant = grant))
        assertEquals("com.game", verified.getString("caller"))
        assertTrue(byName(verified, "presets", "Calm").getBoolean("allowed"))
        assertFalse(byName(verified, "presets", "Aurora").getBoolean("allowed"))
        assertTrue(byName(verified, "effects", "AMBIENT").getBoolean("allowed"))
    }

    @Test fun globalSwitchAllowsAll() {
        val o = ExternalQuery.build(inputs(privileged = true))
        assertTrue(byName(o, "presets", "Aurora").getBoolean("allowed"))
        assertTrue(byName(o, "effects", "AMBIAURORA").getBoolean("allowed"))
    }
}
