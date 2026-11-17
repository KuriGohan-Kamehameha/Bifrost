package com.moonbench.bifrost.suggest

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThemeSuggestionsTest {
    private fun palette(accent: Int) = CocoonTheme.Palette(accent, accent, 0, 0, 0, -1)
    private fun owned(arr: JSONArray, owner: String) =
        OwnedPresets.objects(arr).filter { it.optString("ownerPackage") == owner }
    private fun rgb(c: Int) = Triple(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)

    @Test fun greyPaletteHasNoLedColour() {
        assertNull(CocoonTheme.ledColor(0xFF808080.toInt()))
        assertNull(CocoonTheme.ledColor(0xFF000000.toInt()))
        assertNull(CocoonTheme.ledColor(0xFF7F7A80.toInt()))
    }

    @Test fun pastelAccentBecomesFullStrengthSameHue() {
        val (r, g, b) = rgb(CocoonTheme.ledColor(0xFF90BDF8.toInt())!!) // pastel blue
        assertEquals(255, b)
        assertTrue(r < 60 && g in 100..200)
        assertEquals(0xFFFF0000.toInt(), CocoonTheme.ledColor(0xFFFF0000.toInt()))
        assertEquals(0xFF00FF00.toInt(), CocoonTheme.ledColor(0xFF00FF00.toInt()))
        val (pr, pg, pb) = rgb(CocoonTheme.ledColor(0xFFB39DDB.toInt())!!) // pastel purple
        assertTrue(pb == 255 && pr > pg)
    }

    @Test fun cocoonPresetBecomesFallbackOnlyWhenNoneChosen() {
        val created = CocoonTheme.upsert(JSONArray(), palette(0xFF3366FF.toInt()))
        assertTrue(created.changed)
        assertTrue(owned(created.presets, CocoonTheme.OWNER).single().getBoolean("isAppProfileDefault"))
        val userDefault = JSONArray().put(JSONObject().put("name", "Idle").put("isAppProfileDefault", true))
        val second = CocoonTheme.upsert(userDefault, palette(0xFF3366FF.toInt()))
        assertFalse(owned(second.presets, CocoonTheme.OWNER).single().getBoolean("isAppProfileDefault"))
        assertTrue(OwnedPresets.objects(second.presets).single { it.optString("name") == "Idle" }
            .getBoolean("isAppProfileDefault"))
    }

    @Test fun cocoonPresetFollowsPaletteWithoutDuplicating() {
        val first = CocoonTheme.upsert(JSONArray(), palette(0xFF3366FF.toInt()))
        val same = CocoonTheme.upsert(first.presets, palette(0xFF3366FF.toInt()))
        assertFalse(same.changed)
        val red = CocoonTheme.upsert(first.presets, palette(0xFFFF3333.toInt()))
        assertTrue(red.changed)
        val preset = owned(red.presets, CocoonTheme.OWNER).single()
        assertEquals(CocoonTheme.ledColor(0xFFFF3333.toInt()), preset.getInt("color"))
        assertEquals(preset.getInt("color"), preset.getInt("rightColor"))
        val grey = CocoonTheme.upsert(red.presets, palette(0xFF808080.toInt()))
        assertFalse(grey.changed)
        assertNull(grey.name)
    }

    @Test fun cocoonNeverTakesAUserName() {
        val user = JSONArray().put(JSONObject().put("name", "Cocoon Match").put("color", 1))
        val out = CocoonTheme.upsert(user, palette(0xFF3366FF.toInt()))
        assertEquals("Cocoon Match (Bifrost)", out.name)
        assertEquals(1, OwnedPresets.objects(out.presets).single { it.optString("name") == "Cocoon Match" }.getInt("color"))
        val removed = CocoonTheme.remove(out.presets, mapOf("a" to "Cocoon Match (Bifrost)", "b" to "Cocoon Match"))
        assertEquals(mapOf("b" to "Cocoon Match"), removed.mappings)
        assertEquals(1, removed.presets.length())
    }

    @Test fun thorPurpleInstallsOnceAndUndoes() {
        val user = JSONArray().put(JSONObject().put("name", "Thor Purple").put("color", 7))
        val first = ThorPurple.install(user)
        assertTrue(first.created)
        assertEquals("Thor Purple (Bifrost)", first.name)
        val preset = owned(first.presets, ThorPurple.OWNER).single()
        assertEquals(ThorPurple.COLOR, preset.getInt("color"))
        assertEquals(ThorPurple.COLOR, preset.getInt("rightColor"))
        val again = ThorPurple.install(first.presets)
        assertFalse(again.created)
        assertEquals(2, again.presets.length())
        val removed = ThorPurple.remove(again.presets, mapOf("x" to "Thor Purple (Bifrost)"))
        assertEquals(1, removed.presets.length())
        assertTrue(removed.mappings.isEmpty())
        assertEquals(7, OwnedPresets.objects(removed.presets).single().getInt("color"))
    }

    @Test fun ownersAreIndependent() {
        val withAll = ThorPurple.install(
            CocoonTheme.upsert(
                SwitchProfilePlan.apply(JSONArray(), emptyMap(),
                    SwitchProfilePlan.plan(JSONArray(), emptyMap(), listOf("dev.igawa6.edenduo")), false).presets,
                palette(0xFF3366FF.toInt())).presets).presets
        assertEquals(3, withAll.length())
        val noSwitch = SwitchProfilePlan.remove(withAll, emptyMap()).presets
        assertEquals(2, noSwitch.length())
        assertEquals(1, owned(noSwitch, CocoonTheme.OWNER).size)
        assertEquals(1, owned(noSwitch, ThorPurple.OWNER).size)
    }
}
