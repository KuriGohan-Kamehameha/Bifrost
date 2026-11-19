package com.moonbench.bifrost.revive

import org.json.JSONObject

/**
 * The LED service's last full start request, reduced to its primitive extras,
 * so a revived service can run exactly what was running before.
 *
 * Deliberately left out:
 * - MediaProjection consent (`resultCode`/`data`): single-use and tied to the
 *   process that received it. A revived capture preset waits for new consent.
 * - External-override extras (`external.*`): an override is a lease of about
 *   1.5 s that its owner keeps renewing. It cannot still be valid once a dead
 *   process has been restarted, and a live owner re-sends it on its next beat.
 * - Revive bookkeeping extras.
 *
 * Values keep their exact type (Intent.getFloatExtra ignores an Int extra).
 */
class ReviveSnapshot private constructor(val values: Map<String, Any>) {

    /** Applies a live parameter update (ACTION_UPDATE_PARAMS) on top. */
    fun merge(updates: Map<String, Any?>): ReviveSnapshot =
        ReviveSnapshot(values + persistable(updates))

    fun toJson(): String {
        val extras = JSONObject()
        for ((key, value) in values) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "z").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "j").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is Double -> entry.put("t", "d").put("v", value)
                is String -> entry.put("t", "s").put("v", value)
                else -> continue
            }
            extras.put(key, entry)
        }
        return JSONObject().put("version", VERSION).put("extras", extras).toString()
    }

    override fun equals(other: Any?): Boolean = other is ReviveSnapshot && other.values == values
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = "ReviveSnapshot($values)"

    companion object {
        private const val VERSION = 1

        /** Intent extras that must never be replayed. */
        val EXCLUDED_KEYS = setOf("resultCode", "data")
        private val EXCLUDED_PREFIXES = listOf("external.", "bifrost.revive")

        fun isPersistable(key: String, value: Any?): Boolean {
            if (key in EXCLUDED_KEYS) return false
            if (EXCLUDED_PREFIXES.any { key.startsWith(it) }) return false
            return value is Boolean || value is Int || value is Long ||
                value is Float || value is Double || value is String
        }

        fun fromExtras(extras: Map<String, Any?>): ReviveSnapshot =
            ReviveSnapshot(persistable(extras))

        fun fromJson(raw: String?): ReviveSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                if (root.optInt("version", -1) != VERSION) return null
                val extras = root.getJSONObject("extras")
                val values = LinkedHashMap<String, Any>()
                for (key in extras.keys()) {
                    val entry = extras.getJSONObject(key)
                    val value: Any = when (entry.getString("t")) {
                        "z" -> entry.getBoolean("v")
                        "i" -> entry.getInt("v")
                        "j" -> entry.getLong("v")
                        "f" -> entry.getDouble("v").toFloat()
                        "d" -> entry.getDouble("v")
                        "s" -> entry.getString("v")
                        else -> continue
                    }
                    if (isPersistable(key, value)) values[key] = value
                }
                ReviveSnapshot(values)
            }.getOrNull()
        }

        private fun persistable(extras: Map<String, Any?>): Map<String, Any> {
            val out = LinkedHashMap<String, Any>()
            for ((key, value) in extras) {
                if (value != null && isPersistable(key, value)) out[key] = value
            }
            return out
        }
    }
}
