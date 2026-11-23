package com.moonbench.bifrost.revive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviveSnapshotTest {

    private val startExtras: Map<String, Any?> = mapOf(
        "animationType" to "AMBIENT",
        "performanceProfile" to "HIGH",
        "animationColor" to -16776961,
        "brightness" to 200,
        "speed" to 0.25f,
        "smoothness" to 0.5f,
        "useSingleColor" to true,
        "ambientDisplayId" to 0,
        "allowBackgroundRun" to true,
        "someLong" to 42L,
        "someDouble" to 1.5,
        // Must never be replayed:
        "resultCode" to -1,
        "data" to Any(),
        "external.effect" to "PIPBOY",
        "external.durationMs" to 1500L,
        ServiceRevive.EXTRA_REVIVE_SOURCE to "system restart",
        ServiceRevive.EXTRA_REVIVE_CAUSE to "low memory",
        "nothing" to null
    )

    @Test fun restoresThePresetWithExactTypes() {
        val restored = ReviveSnapshot.fromJson(ReviveSnapshot.fromExtras(startExtras).toJson())!!
        assertEquals("AMBIENT", restored.values["animationType"])
        assertEquals(200, restored.values["brightness"])
        // Intent.getFloatExtra ignores an Int or Double extra, so the type matters.
        assertEquals(0.25f, restored.values["speed"])
        assertTrue(restored.values["speed"] is Float)
        assertEquals(42L, restored.values["someLong"])
        assertTrue(restored.values["someLong"] is Long)
        assertEquals(1.5, restored.values["someDouble"])
        assertEquals(true, restored.values["useSingleColor"])
        assertEquals(true, restored.values["allowBackgroundRun"])
    }

    @Test fun neverPersistsProjectionConsentOrExternalOverrides() {
        val values = ReviveSnapshot.fromExtras(startExtras).values
        assertFalse(values.containsKey("resultCode"))
        assertFalse(values.containsKey("data"))
        assertFalse(values.keys.any { it.startsWith("external.") })
        assertFalse(values.containsKey(ServiceRevive.EXTRA_REVIVE_SOURCE))
        assertFalse(values.containsKey(ServiceRevive.EXTRA_REVIVE_CAUSE))
        assertFalse(values.containsKey("nothing"))
    }

    @Test fun liveUpdatesAreMergedOverTheStartRequest() {
        val merged = ReviveSnapshot.fromExtras(startExtras)
            .merge(mapOf("brightness" to 90, "speed" to 0.9f, "external.color" to 1))
        assertEquals(90, merged.values["brightness"])
        assertEquals(0.9f, merged.values["speed"])
        assertEquals("AMBIENT", merged.values["animationType"])
        assertFalse(merged.values.containsKey("external.color"))
    }

    @Test fun corruptOrForeignJsonIsIgnored() {
        assertNull(ReviveSnapshot.fromJson(null))
        assertNull(ReviveSnapshot.fromJson(""))
        assertNull(ReviveSnapshot.fromJson("not json"))
        assertNull(ReviveSnapshot.fromJson("""{"version":99,"extras":{}}"""))
    }

    @Test fun excludedKeysInStoredJsonAreDropped() {
        val raw = """{"version":1,"extras":{"brightness":{"t":"i","v":5},"resultCode":{"t":"i","v":-1}}}"""
        val restored = ReviveSnapshot.fromJson(raw)!!
        assertEquals(mapOf<String, Any>("brightness" to 5), restored.values)
    }
}
