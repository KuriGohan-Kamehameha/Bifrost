package com.moonbench.bifrost.suggest

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure helpers for presets that Bifrost itself creates on the user's behalf
 * (Switch profile, Cocoon match, Thor Purple). They live in the shared
 * `presets_json` list tagged with an `ownerPackage` that can never be an
 * Android package name (it contains ':'), exactly like plugin presets
 * ("plugin:<id>"), so removal touches only what Bifrost created.
 *
 * Every function returns new objects; inputs are never mutated.
 */
object OwnedPresets {

    const val KEY_OWNER = "ownerPackage"

    fun parse(raw: String?): JSONArray =
        if (raw.isNullOrBlank()) JSONArray() else runCatching { JSONArray(raw) }.getOrDefault(JSONArray())

    fun copy(presets: JSONArray): JSONArray = JSONArray(presets.toString())

    fun objects(presets: JSONArray): List<JSONObject> =
        (0 until presets.length()).mapNotNull { presets.optJSONObject(it) }

    fun names(presets: JSONArray): Set<String> =
        objects(presets).map { it.optString("name") }.filter { it.isNotBlank() }.toSet()

    fun findOwned(presets: JSONArray, owner: String): JSONObject? =
        objects(presets).firstOrNull { it.optString(KEY_OWNER) == owner }

    fun hasDefault(presets: JSONArray): Boolean =
        objects(presets).any { it.optBoolean("isAppProfileDefault", false) }

    /**
     * First candidate not already used by another preset; if all are taken,
     * the last candidate with an increasing number. Never returns a taken name,
     * so a user's preset of the same name is never shadowed or overwritten.
     */
    fun freeName(taken: Set<String>, candidates: List<String>): String {
        require(candidates.isNotEmpty())
        candidates.firstOrNull { it !in taken }?.let { return it }
        val base = candidates.last()
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    /** A dual-colour STATIC preset in PresetController's stored format. */
    fun staticPreset(
        name: String,
        owner: String,
        left: Int,
        right: Int,
        emoji: String,
        isAppProfileDefault: Boolean = false
    ): JSONObject = JSONObject().apply {
        put("name", name)
        put("animationType", "STATIC")
        put("performanceProfile", "HIGH")
        put("color", left)
        put("rightColor", right)
        put("brightness", 255)
        put("speed", 0.5)
        put("smoothness", 0.5)
        put("useSingleColor", false)
        put("isAppProfileDefault", isAppProfileDefault)
        put("ragnarokAccepted", false)
        put("icon", "LIGHT")
        put("customEmoji", emoji)
        put(KEY_OWNER, owner)
    }

    data class Ensured(val presets: JSONArray, val name: String, val created: Boolean)

    /**
     * Returns the list with exactly one preset owned by [owner]: the existing
     * one (kept as-is, including user tweaks) or a new one built by [factory]
     * under a free name. Never duplicates, never overwrites another preset.
     */
    fun ensure(
        presets: JSONArray,
        owner: String,
        candidates: List<String>,
        factory: (name: String) -> JSONObject
    ): Ensured {
        val out = copy(presets)
        findOwned(out, owner)?.let { return Ensured(out, it.optString("name"), created = false) }
        val name = freeName(names(out), candidates)
        out.put(factory(name))
        return Ensured(out, name, created = true)
    }

    data class Removed(val presets: JSONArray, val removedNames: List<String>)

    fun removeOwned(presets: JSONArray, owner: String): Removed {
        val out = JSONArray()
        val removed = mutableListOf<String>()
        objects(presets).forEach { obj ->
            if (obj.optString(KEY_OWNER) == owner) removed.add(obj.optString("name")) else out.put(obj)
        }
        return Removed(out, removed)
    }

    /** Mappings left after dropping every app mapped to one of [names]. */
    fun mappingsWithout(mappings: Map<String, String>, names: Collection<String>): Map<String, String> {
        if (names.isEmpty()) return mappings
        val set = names.toHashSet()
        return mappings.filterValues { it !in set }
    }
}
