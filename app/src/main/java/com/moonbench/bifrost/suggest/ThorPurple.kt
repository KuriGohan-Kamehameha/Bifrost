package com.moonbench.bifrost.suggest

import org.json.JSONArray

/**
 * "Thor Purple": a purple preset plus Bifrost's existing OLED Purple UI theme,
 * for the purple AYN Thor shell.
 *
 * Manual only. No readable hardware signifier of the shell colour was found on
 * a Thor (firmware Thor_V1.0.0.377): ro.product.* / ro.boot.* (sku "kalama"),
 * persist.sys.product, the device-tree model/compatible ("KalamaP HDK") and the
 * AYN settings keys identify the SoC/board and region, not the colour. The
 * settings key joystick_led_light_picker_color is the LED colour the user
 * picked, not the shell. So Bifrost does not guess; it offers this in Settings.
 */
object ThorPurple {

    const val OWNER = "bifrost:thor-purple"
    val NAME_CANDIDATES = listOf("Thor Purple", "Thor Purple (Bifrost)")
    const val EMOJI = "💜" // purple heart
    const val COLOR = 0xFF7F00FF.toInt()
    const val UI_THEME_ID = "oled_purple"

    fun install(presets: JSONArray): OwnedPresets.Ensured =
        OwnedPresets.ensure(presets, OWNER, NAME_CANDIDATES) { name ->
            OwnedPresets.staticPreset(name, OWNER, COLOR, COLOR, EMOJI)
        }

    fun remove(presets: JSONArray, mappings: Map<String, String>): SwitchProfilePlan.Outcome {
        val removed = OwnedPresets.removeOwned(presets, OWNER)
        val kept = OwnedPresets.mappingsWithout(mappings, removed.removedNames)
        return SwitchProfilePlan.Outcome(
            removed.presets, kept, removed.removedNames.firstOrNull(),
            mappings.keys.filter { it !in kept })
    }
}
