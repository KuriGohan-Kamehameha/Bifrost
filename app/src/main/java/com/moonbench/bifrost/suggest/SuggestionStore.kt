package com.moonbench.bifrost.suggest

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.moonbench.bifrost.services.AppProfileManager
import org.json.JSONArray

/**
 * SharedPreferences glue for the setup suggestions (Switch profile, Cocoon
 * match, Thor Purple). All decisions live in the pure planners; this only
 * reads/writes `presets_json`, the app-profile mappings and a few flags in the
 * shared "bifrost_prefs". Callers on the UI must reload PresetController after
 * a change (as after a plugin install).
 */
object SuggestionStore {

    private const val PREF_PRESETS = "presets_json"
    const val PREF_SELECTED_UI_THEME = "selected_ui_theme"

    private const val PREF_SWITCH_DISABLED = "switch_profile_prompt_disabled"
    private const val PREF_SWITCH_OFFERED = "switch_profile_offered_packages"
    private const val PREF_COCOON_ENABLED = "cocoon_match_enabled"
    private const val PREF_COCOON_PROMPT_SHOWN = "cocoon_match_prompt_shown"
    private const val PREF_COCOON_PREVIOUS_THEME = "cocoon_match_previous_ui_theme"
    private const val PREF_THOR_PURPLE_PREVIOUS_THEME = "thor_purple_previous_ui_theme"

    private val lock = Any()

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)

    fun presets(prefs: SharedPreferences): JSONArray = OwnedPresets.parse(prefs.getString(PREF_PRESETS, null))

    private fun savePresets(prefs: SharedPreferences, presets: JSONArray) {
        prefs.edit { putString(PREF_PRESETS, presets.toString()) }
    }

    // ---- Switch profile ---------------------------------------------------

    fun hasSwitchProfile(prefs: SharedPreferences): Boolean =
        OwnedPresets.findOwned(presets(prefs), SwitchProfilePlan.OWNER) != null

    fun switchPromptDisabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SWITCH_DISABLED, false)

    fun setSwitchPromptDisabled(prefs: SharedPreferences, disabled: Boolean) {
        prefs.edit { putBoolean(PREF_SWITCH_DISABLED, disabled) }
    }

    fun offeredSwitchPackages(prefs: SharedPreferences): Set<String> {
        val arr = runCatching { JSONArray(prefs.getString(PREF_SWITCH_OFFERED, "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
    }

    fun markSwitchOffered(prefs: SharedPreferences, packages: Collection<String>) {
        val all = (offeredSwitchPackages(prefs) + packages).sorted()
        prefs.edit { putString(PREF_SWITCH_OFFERED, JSONArray(all).toString()) }
    }

    fun pendingSwitchOffer(context: Context, prefs: SharedPreferences): List<String> =
        SwitchProfilePlan.pendingOffer(
            SwitchEmulators.installed(context).map { it.packageName },
            AppProfileManager(prefs).getMappings(),
            offeredSwitchPackages(prefs),
            switchPromptDisabled(prefs)
        )

    fun planSwitch(prefs: SharedPreferences, detected: List<String>): SwitchProfilePlan.Plan =
        SwitchProfilePlan.plan(presets(prefs), AppProfileManager(prefs).getMappings(), detected)

    fun applySwitch(
        prefs: SharedPreferences,
        detected: List<String>,
        remapConflicts: Boolean
    ): SwitchProfilePlan.Outcome = synchronized(lock) {
        val apm = AppProfileManager(prefs)
        val presets = presets(prefs)
        val mappings = apm.getMappings()
        val plan = SwitchProfilePlan.plan(presets, mappings, detected)
        val outcome = SwitchProfilePlan.apply(presets, mappings, plan, remapConflicts)
        savePresets(prefs, outcome.presets)
        apm.replaceMappings(outcome.mappings)
        markSwitchOffered(prefs, detected)
        outcome
    }

    fun removeSwitch(prefs: SharedPreferences): SwitchProfilePlan.Outcome = synchronized(lock) {
        val apm = AppProfileManager(prefs)
        val outcome = SwitchProfilePlan.remove(presets(prefs), apm.getMappings())
        savePresets(prefs, outcome.presets)
        apm.replaceMappings(outcome.mappings)
        outcome
    }

    // ---- Cocoon match -----------------------------------------------------

    fun cocoonMatchEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_COCOON_ENABLED, false)

    fun cocoonPromptShown(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_COCOON_PROMPT_SHOWN, false)

    fun markCocoonPromptShown(prefs: SharedPreferences) {
        prefs.edit { putBoolean(PREF_COCOON_PROMPT_SHOWN, true) }
    }

    /**
     * Turns matching on: writes the neutral preset from the current palette and
     * remembers the UI theme to restore. Returns null (and changes nothing)
     * when the system palette cannot be read.
     */
    fun enableCocoonMatch(context: Context, prefs: SharedPreferences): CocoonTheme.Outcome? = synchronized(lock) {
        val palette = CocoonTheme.readSystemPalette(context) ?: return null
        val outcome = CocoonTheme.upsert(presets(prefs), palette)
        savePresets(prefs, outcome.presets)
        val current = prefs.getString(PREF_SELECTED_UI_THEME, null)
        prefs.edit {
            putBoolean(PREF_COCOON_ENABLED, true)
            putString(PREF_COCOON_PREVIOUS_THEME, current.takeUnless { it == CocoonTheme.UI_THEME_ID })
            putString(PREF_SELECTED_UI_THEME, CocoonTheme.UI_THEME_ID)
        }
        outcome
    }

    /** Re-syncs the neutral preset with the palette; true if it changed. */
    fun refreshCocoonMatch(context: Context, prefs: SharedPreferences): Boolean = synchronized(lock) {
        if (!cocoonMatchEnabled(prefs)) return false
        val palette = CocoonTheme.readSystemPalette(context) ?: return false
        val outcome = CocoonTheme.upsert(presets(prefs), palette)
        if (outcome.changed) savePresets(prefs, outcome.presets)
        outcome.changed
    }

    /** Turns matching off, removes the neutral preset and restores the UI theme. */
    fun disableCocoonMatch(prefs: SharedPreferences): String? = synchronized(lock) {
        val apm = AppProfileManager(prefs)
        val outcome = CocoonTheme.remove(presets(prefs), apm.getMappings())
        savePresets(prefs, outcome.presets)
        apm.replaceMappings(outcome.mappings)
        val restore = prefs.getString(PREF_COCOON_PREVIOUS_THEME, null)
        val restoreTheme = prefs.getString(PREF_SELECTED_UI_THEME, null) == CocoonTheme.UI_THEME_ID
        prefs.edit {
            putBoolean(PREF_COCOON_ENABLED, false)
            remove(PREF_COCOON_PREVIOUS_THEME)
            if (restoreTheme) {
                if (restore != null) putString(PREF_SELECTED_UI_THEME, restore) else remove(PREF_SELECTED_UI_THEME)
            }
        }
        outcome.presetName
    }

    // ---- Thor Purple ------------------------------------------------------

    fun hasThorPurple(prefs: SharedPreferences): Boolean =
        OwnedPresets.findOwned(presets(prefs), ThorPurple.OWNER) != null

    fun applyThorPurple(prefs: SharedPreferences): OwnedPresets.Ensured = synchronized(lock) {
        val ensured = ThorPurple.install(presets(prefs))
        savePresets(prefs, ensured.presets)
        val current = prefs.getString(PREF_SELECTED_UI_THEME, null)
        prefs.edit {
            putString(PREF_THOR_PURPLE_PREVIOUS_THEME, current.takeUnless { it == ThorPurple.UI_THEME_ID })
            putString(PREF_SELECTED_UI_THEME, ThorPurple.UI_THEME_ID)
        }
        ensured
    }

    fun removeThorPurple(prefs: SharedPreferences): String? = synchronized(lock) {
        val apm = AppProfileManager(prefs)
        val outcome = ThorPurple.remove(presets(prefs), apm.getMappings())
        savePresets(prefs, outcome.presets)
        apm.replaceMappings(outcome.mappings)
        val restore = prefs.getString(PREF_THOR_PURPLE_PREVIOUS_THEME, null)
        val restoreTheme = prefs.getString(PREF_SELECTED_UI_THEME, null) == ThorPurple.UI_THEME_ID
        prefs.edit {
            remove(PREF_THOR_PURPLE_PREVIOUS_THEME)
            if (restoreTheme) {
                if (restore != null) putString(PREF_SELECTED_UI_THEME, restore) else remove(PREF_SELECTED_UI_THEME)
            }
        }
        outcome.presetName
    }
}
