package com.moonbench.bifrost.suggest

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SwitchProfilePlanTest {
    private val eden = "dev.eden.eden_emulator"
    private val nightly = "dev.eden.eden_emulator.nightly"
    private val duo = "dev.igawa6.edenduo"

    private fun userPreset(name: String, color: Int = 0xFF112233.toInt()) =
        JSONObject().put("name", name).put("animationType", "STATIC").put("color", color)

    private fun presets(vararg objs: JSONObject) = JSONArray().apply { objs.forEach { put(it) } }

    private fun owned(arr: JSONArray) =
        OwnedPresets.objects(arr).filter { it.optString("ownerPackage") == SwitchProfilePlan.OWNER }

    @Test fun createsJoyConPresetAndMapsEveryEmulator() {
        val start = presets(userPreset("Reading"))
        val plan = SwitchProfilePlan.plan(start, emptyMap(), listOf(nightly, duo))
        assertTrue(plan.createPreset)
        assertEquals("Nintendo Switch", plan.presetName)
        val out = SwitchProfilePlan.apply(start, emptyMap(), plan, remapConflicts = false)
        val preset = owned(out.presets).single()
        assertEquals("STATIC", preset.getString("animationType"))
        assertEquals(SwitchProfilePlan.LEFT_COLOR, preset.getInt("color"))
        assertEquals(SwitchProfilePlan.RIGHT_COLOR, preset.getInt("rightColor"))
        assertFalse(preset.getBoolean("isAppProfileDefault"))
        assertEquals(mapOf(nightly to "Nintendo Switch", duo to "Nintendo Switch"), out.mappings)
        assertEquals(2, out.presets.length())
        assertEquals("input is not mutated", 1, start.length())
    }

    @Test fun rerunUpdatesWithoutDuplicatingAndKeepsUserTweaks() {
        val first = SwitchProfilePlan.apply(
            JSONArray(), emptyMap(), SwitchProfilePlan.plan(JSONArray(), emptyMap(), listOf(nightly)), false)
        owned(first.presets).single().put("brightness", 40)
        val plan = SwitchProfilePlan.plan(first.presets, first.mappings, listOf(nightly, eden))
        assertFalse(plan.createPreset)
        assertEquals(listOf(eden), plan.toMap)
        assertEquals(listOf(nightly), plan.alreadyMapped)
        val second = SwitchProfilePlan.apply(first.presets, first.mappings, plan, false)
        assertEquals(1, owned(second.presets).size)
        assertEquals(40, owned(second.presets).single().getInt("brightness"))
        assertEquals("Nintendo Switch", second.mappings[eden])
        val again = SwitchProfilePlan.plan(second.presets, second.mappings, listOf(nightly, eden))
        assertFalse(again.hasWork)
    }

    @Test fun neverOverwritesUserPresetOfTheSameName() {
        val start = presets(userPreset("Nintendo Switch", 0xFF00FF00.toInt()))
        val plan = SwitchProfilePlan.plan(start, emptyMap(), listOf(duo))
        assertEquals("Nintendo Switch (Joy-Con)", plan.presetName)
        val out = SwitchProfilePlan.apply(start, emptyMap(), plan, false)
        val user = OwnedPresets.objects(out.presets).single { it.optString("name") == "Nintendo Switch" }
        assertEquals(0xFF00FF00.toInt(), user.getInt("color"))
        assertFalse(user.has("ownerPackage"))
        assertEquals("Nintendo Switch (Joy-Con)", out.mappings[duo])
        val both = presets(userPreset("Nintendo Switch"), userPreset("Nintendo Switch (Joy-Con)"))
        assertEquals("Nintendo Switch (Joy-Con) 2", SwitchProfilePlan.plan(both, emptyMap(), listOf(duo)).presetName)
    }

    @Test fun userMappingIsOnlyReplacedWhenTheUserAgrees() {
        val start = presets(userPreset("My Duo"))
        val mappings = mapOf(duo to "My Duo", "com.other" to "My Duo")
        val plan = SwitchProfilePlan.plan(start, mappings, listOf(duo, nightly))
        assertEquals(mapOf(duo to "My Duo"), plan.conflicts)
        assertEquals(listOf(nightly), plan.toMap)
        val kept = SwitchProfilePlan.apply(start, mappings, plan, remapConflicts = false)
        assertEquals("My Duo", kept.mappings[duo])
        assertEquals("Nintendo Switch", kept.mappings[nightly])
        assertEquals("My Duo", kept.mappings["com.other"])
        val remapped = SwitchProfilePlan.apply(start, mappings, plan, remapConflicts = true)
        assertEquals("Nintendo Switch", remapped.mappings[duo])
        assertEquals("My Duo", remapped.mappings["com.other"])
    }

    @Test fun mappingToADeletedPresetCountsAsUnmapped() {
        val plan = SwitchProfilePlan.plan(JSONArray(), mapOf(duo to "Gone"), listOf(duo))
        assertEquals(listOf(duo), plan.toMap)
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test fun undoRemovesOnlyTheOwnedPresetAndItsMappings() {
        val start = presets(userPreset("Reading"))
        val installed = SwitchProfilePlan.apply(
            start, mapOf("com.book" to "Reading"),
            SwitchProfilePlan.plan(start, mapOf("com.book" to "Reading"), listOf(duo, nightly)), false)
        val removed = SwitchProfilePlan.remove(installed.presets, installed.mappings)
        assertEquals("Nintendo Switch", removed.presetName)
        assertTrue(owned(removed.presets).isEmpty())
        assertEquals(1, removed.presets.length())
        assertEquals(mapOf("com.book" to "Reading"), removed.mappings)
        assertEquals(setOf(duo, nightly), removed.changedPackages.toSet())
        val nothing = SwitchProfilePlan.remove(removed.presets, removed.mappings)
        assertNull(nothing.presetName)
        assertEquals(removed.mappings, nothing.mappings)
    }

    @Test fun promptOnlyForNewUnmappedEmulatorsUnlessDisabled() {
        val detected = listOf(nightly, duo)
        assertEquals(detected, SwitchProfilePlan.pendingOffer(detected, emptyMap(), emptySet(), false))
        assertEquals(listOf(duo), SwitchProfilePlan.pendingOffer(detected, emptyMap(), setOf(nightly), false))
        assertEquals(listOf(nightly),
            SwitchProfilePlan.pendingOffer(detected, mapOf(duo to "Anything"), emptySet(), false))
        assertTrue(SwitchProfilePlan.pendingOffer(detected, emptyMap(), emptySet(), true).isEmpty())
        assertTrue(SwitchProfilePlan.pendingOffer(emptyList(), emptyMap(), emptySet(), false).isEmpty())
    }
}
