package com.moonbench.bifrost.plugins

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the `.bfplugin` manifest `externalAccess` block and its prefs store.
 * The block is a cross-repo contract with the plugins repo, like livePolicies.
 */
class ExternalGrantTest {

    private val manifestBlock = """
        {
          "com.example.game": { "presets": ["Cutscene Aurora", " ", "*"], "effects": ["AMBIENT"] },
          "broken": 7
        }
    """.trimIndent()

    @Test fun parsesBlockAndSkipsBadEntries() {
        val warnings = mutableListOf<String>()
        val grants = ExternalGrant.parseBlock(JSONObject(manifestBlock), warnings)
        assertEquals(setOf("com.example.game"), grants.keys)
        val g = grants.getValue("com.example.game")
        assertEquals(setOf("Cutscene Aurora", "*"), g.presets)   // blank dropped
        assertEquals(setOf("AMBIENT"), g.effects)
        assertEquals(1, warnings.size)
    }

    @Test fun missingBlockIsEmpty() {
        assertTrue(ExternalGrant.parseBlock(null, mutableListOf()).isEmpty())
    }

    @Test fun missingListsGrantNothing() {
        val g = ExternalGrant.fromJson(JSONObject("{}"))
        assertTrue(!g.allowsPreset("x") && !g.allowsEffect("AMBIENT"))
    }

    @Test fun storeRoundTripAndOwnerRemoval() {
        val grants = mapOf("com.a" to ExternalGrant(setOf("P"), setOf("AMBIENT")))
        var raw = ExternalGrantStore.merge(null, "plugin-a", grants)
        raw = ExternalGrantStore.merge(raw, "plugin-b", mapOf("com.b" to ExternalGrant(setOf("*"))))

        val a = ExternalGrantStore.forPackage(raw, "com.a")!!
        assertTrue(a.allowsPreset("P") && a.allowsEffect("AMBIENT"))
        assertTrue(ExternalGrantStore.forPackage(raw, "com.b")!!.allowsPreset("Q"))

        val afterA = ExternalGrantStore.withoutOwner(raw, "plugin-a")!!
        assertNull(ExternalGrantStore.forPackage(afterA, "com.a"))
        assertTrue(ExternalGrantStore.forPackage(afterA, "com.b") != null)
        assertNull(ExternalGrantStore.withoutOwner(afterA, "plugin-a"))   // nothing left to remove
    }

    @Test fun garbagePrefsReadAsNoGrant() {
        assertNull(ExternalGrantStore.forPackage("not json", "com.a"))
        assertNull(ExternalGrantStore.forPackage(null, "com.a"))
    }
}
