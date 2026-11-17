package com.moonbench.bifrost.suggest

import org.json.JSONArray

/**
 * Pure planning for the "Nintendo Switch" profile: one Bifrost-owned STATIC
 * preset (left stick Joy-Con neon blue, right stick neon red) plus app-profile
 * mappings from each detected emulator to it.
 *
 * Rules: reuse the owned preset if it exists (never duplicate, keep the user's
 * tweaks to it); never take the name of a preset Bifrost does not own; map only
 * emulators that are unmapped (or mapped to a preset that no longer exists);
 * an emulator the user mapped to another existing preset is a conflict that is
 * only remapped when the user explicitly agrees.
 */
object SwitchProfilePlan {

    const val OWNER = "bifrost:switch-profile"
    val NAME_CANDIDATES = listOf("Nintendo Switch", "Nintendo Switch (Joy-Con)")
    const val EMOJI = "🎮" // video game controller

    /** Joy-Con neon blue (left) and neon red (right), as Nintendo's UI renders them. */
    const val LEFT_COLOR = 0xFF00C3E3.toInt()
    const val RIGHT_COLOR = 0xFFFF4554.toInt()

    data class Plan(
        val presetName: String,
        val createPreset: Boolean,
        val toMap: List<String>,
        val alreadyMapped: List<String>,
        val conflicts: Map<String, String>
    ) {
        val hasWork: Boolean get() = createPreset || toMap.isNotEmpty() || conflicts.isNotEmpty()
    }

    data class Outcome(
        val presets: JSONArray,
        val mappings: Map<String, String>,
        val presetName: String?,
        val changedPackages: List<String>
    )

    fun plan(presets: JSONArray, mappings: Map<String, String>, detected: List<String>): Plan {
        val owned = OwnedPresets.findOwned(presets, OWNER)
        val existingNames = OwnedPresets.names(presets)
        val name = owned?.optString("name")
            ?: OwnedPresets.freeName(existingNames, NAME_CANDIDATES)
        val toMap = mutableListOf<String>()
        val already = mutableListOf<String>()
        val conflicts = linkedMapOf<String, String>()
        detected.distinct().forEach { pkg ->
            val current = mappings[pkg]
            when {
                current == null || current !in existingNames -> toMap.add(pkg)
                current == name && owned != null -> already.add(pkg)
                else -> conflicts[pkg] = current
            }
        }
        return Plan(name, owned == null, toMap, already, conflicts)
    }

    fun apply(
        presets: JSONArray,
        mappings: Map<String, String>,
        plan: Plan,
        remapConflicts: Boolean
    ): Outcome {
        val ensured = OwnedPresets.ensure(presets, OWNER, NAME_CANDIDATES) { name ->
            OwnedPresets.staticPreset(name, OWNER, LEFT_COLOR, RIGHT_COLOR, EMOJI)
        }
        val targets = plan.toMap + if (remapConflicts) plan.conflicts.keys else emptySet()
        val updated = mappings.toMutableMap()
        targets.forEach { updated[it] = ensured.name }
        return Outcome(ensured.presets, updated, ensured.name, targets)
    }

    /** Undo: drop the owned preset and every mapping that pointed at it. */
    fun remove(presets: JSONArray, mappings: Map<String, String>): Outcome {
        val removed = OwnedPresets.removeOwned(presets, OWNER)
        val kept = OwnedPresets.mappingsWithout(mappings, removed.removedNames)
        val unmapped = mappings.keys.filter { it !in kept }
        return Outcome(removed.presets, kept, removed.removedNames.firstOrNull(), unmapped)
    }

    /**
     * Emulators worth an automatic prompt: installed, not mapped by anyone, and
     * not already offered once. Empty when the user chose "Don't ask again".
     */
    fun pendingOffer(
        detected: List<String>,
        mappings: Map<String, String>,
        offered: Set<String>,
        disabled: Boolean
    ): List<String> {
        if (disabled) return emptyList()
        return detected.filter { it !in mappings && it !in offered }
    }
}
