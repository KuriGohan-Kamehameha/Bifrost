package com.moonbench.bifrost.plugins

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * A plugin-declared grant of the v2 external-API powers (apply presets, start
 * capture-driven effects) to ONE app package, the plugin's companion app.
 *
 * Declared in a `.bfplugin` manifest as
 *
 *   "externalAccess": {
 *     "com.example.game": { "presets": ["Cutscene Aurora"], "effects": ["AMBIENT"] }
 *   }
 *
 * `"*"` in a list means "any". Installing the plugin from the Plugin Store is
 * the user's consent; uninstalling it removes the grant (owner-tagged, exactly
 * like [LivePolicyStore]). A grant is honoured only for a caller whose package
 * Android vouched for (ExternalApi.EXTRA_CALLER_TOKEN or share-identity), never
 * for an unverified one, and only while "Allow third-party LED control" is on.
 */
data class ExternalGrant(
    val presets: Set<String> = emptySet(),
    val effects: Set<String> = emptySet(),
) {
    fun allowsPreset(name: String): Boolean = WILDCARD in presets || name in presets
    fun allowsEffect(name: String): Boolean = WILDCARD in effects || name in effects

    fun toJson(): JSONObject = JSONObject().apply {
        put("presets", JSONArray(presets.sorted()))
        put("effects", JSONArray(effects.sorted()))
    }

    companion object {
        const val WILDCARD = "*"
        /** Bounded so a malformed manifest can't bloat prefs or a scan loop. */
        const val MAX_ENTRIES = 64

        fun fromJson(o: JSONObject): ExternalGrant = ExternalGrant(
            presets = readNames(o.optJSONArray("presets")),
            effects = readNames(o.optJSONArray("effects")),
        )

        private fun readNames(arr: JSONArray?): Set<String> {
            if (arr == null) return emptySet()
            val out = linkedSetOf<String>()
            val n = minOf(arr.length(), MAX_ENTRIES)
            for (i in 0 until n) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) out.add(s)
            }
            return out
        }

        /** Parse a manifest `externalAccess` block (package -> grant). Bad entries are skipped. */
        fun parseBlock(obj: JSONObject?, warnings: MutableList<String>): Map<String, ExternalGrant> {
            if (obj == null) return emptyMap()
            val out = linkedMapOf<String, ExternalGrant>()
            val keys = obj.keys()
            while (keys.hasNext() && out.size < MAX_ENTRIES) {
                val pkg = keys.next().trim()
                val entry = obj.optJSONObject(pkg)
                if (pkg.isEmpty() || entry == null) {
                    warnings += "externalAccess: entry for '$pkg' is not an object; skipping."
                    continue
                }
                out[pkg] = fromJson(entry)
            }
            return out
        }
    }
}

/**
 * Persists plugin-declared [ExternalGrant]s keyed by app package, in the shared
 * prefs as one JSON object. Same shape and ownership rules as [LivePolicyStore].
 */
object ExternalGrantStore {
    const val PREF = "external_grants_json"
    private const val OWNER_KEY = "_ownerPlugin"

    fun forPackage(prefs: SharedPreferences, pkg: String): ExternalGrant? =
        forPackage(prefs.getString(PREF, null), pkg)

    /** Pure form for tests and for callers that already hold the raw JSON. */
    fun forPackage(raw: String?, pkg: String): ExternalGrant? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val entry = root.optJSONObject(pkg) ?: return null
        return ExternalGrant.fromJson(entry)
    }

    fun putAll(prefs: SharedPreferences, ownerPluginId: String, grants: Map<String, ExternalGrant>) {
        if (grants.isEmpty()) return
        val updated = merge(prefs.getString(PREF, null), ownerPluginId, grants)
        prefs.edit().putString(PREF, updated).apply()
    }

    fun removeByOwner(prefs: SharedPreferences, ownerPluginId: String) {
        val raw = prefs.getString(PREF, null) ?: return
        val updated = withoutOwner(raw, ownerPluginId) ?: return
        prefs.edit().putString(PREF, updated).apply()
    }

    /** Pure: [raw] with [grants] added (tagged with [ownerPluginId]). */
    fun merge(raw: String?, ownerPluginId: String, grants: Map<String, ExternalGrant>): String {
        val root = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
        grants.forEach { (pkg, grant) -> root.put(pkg, grant.toJson().put(OWNER_KEY, ownerPluginId)) }
        return root.toString()
    }

    /** Pure: [raw] without the entries [ownerPluginId] installed, or null if nothing changed. */
    fun withoutOwner(raw: String, ownerPluginId: String): String? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val drop = mutableListOf<String>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (root.optJSONObject(k)?.optString(OWNER_KEY) == ownerPluginId) drop.add(k)
        }
        if (drop.isEmpty()) return null
        drop.forEach { root.remove(it) }
        return root.toString()
    }
}
