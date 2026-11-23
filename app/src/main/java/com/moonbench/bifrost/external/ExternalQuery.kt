package com.moonbench.bifrost.external

import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.plugins.ExternalGrant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the ACTION_QUERY answer (the ordered broadcast's resultData). Pure:
 * every live input is passed in, so the wire format is pinned by unit tests.
 *
 * {
 *   "apiVersion": 2, "minApiVersion": 1,
 *   "enabled": true,            // "Allow third-party LED control"
 *   "serviceRunning": true,     // commands are dropped while false
 *   "caller": "com.example",    // verified package, or null
 *   "privileged": false,        // global presets/reactive switch
 *   "capture": { "projection": false, "accessibility": true },
 *   "presets":  [ { "name", "effect", "owner"?, "allowed", "available" } ],
 *   "effects":  [ { "name", "capture": "NONE|SCREEN|PROJECTION", "allowed", "available" } ],
 *   "functions": [ "BRIGHTNESS", "PULSE", "PAUSE", "RESUME" ]
 * }
 *
 * When the master switch is off only the version fields and enabled=false are
 * returned, so a caller can feature-detect without learning the user's presets.
 */
object ExternalQuery {

    /** Upper bound on listed presets; keeps the reply well inside a Binder transaction. */
    const val MAX_PRESETS = 200

    data class Inputs(
        val enabled: Boolean,
        val serviceRunning: Boolean,
        val verifiedCaller: String?,
        val privilegedEnabled: Boolean,
        val grant: ExternalGrant?,
        val hasProjection: Boolean,
        val audioPermitted: Boolean,
        val accessibilityRunning: Boolean,
        val ambientPrefersProjection: Boolean,
        val presetsJson: String?,
    )

    fun build(input: Inputs): JSONObject {
        val out = JSONObject()
        out.put("apiVersion", ExternalApi.API_VERSION)
        out.put("minApiVersion", ExternalApi.MIN_API_VERSION)
        out.put("enabled", input.enabled)
        if (!input.enabled) return out

        out.put("serviceRunning", input.serviceRunning)
        out.put("caller", input.verifiedCaller ?: JSONObject.NULL)
        out.put("privileged", input.privilegedEnabled)
        out.put("capture", JSONObject().apply {
            put("projection", input.hasProjection)
            put("audio", input.audioPermitted)
            put("accessibility", input.accessibilityRunning)
        })

        val verified = input.verifiedCaller != null
        fun available(effect: LedAnimationType) = ExternalCapture.isAvailable(
            effect, input.hasProjection, input.audioPermitted, input.accessibilityRunning, input.ambientPrefersProjection
        )

        val presets = JSONArray()
        val stored = input.presetsJson?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
        val n = minOf(stored.length(), MAX_PRESETS)
        for (i in 0 until n) {
            val obj = stored.optJSONObject(i) ?: continue
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
            val effect = LedAnimationType.fromStoredName(obj.optString("animationType"))
                ?: LedAnimationType.STATIC
            presets.put(JSONObject().apply {
                put("name", name)
                put("effect", effect.name)
                obj.optString("ownerPackage").takeIf { it.isNotBlank() }?.let { put("owner", it) }
                put("allowed", ExternalApiGate.allowsPower(
                    ExternalApiGate.Power.Preset(name), input.privilegedEnabled, verified, input.grant))
                put("available", available(effect))
            })
        }
        out.put("presets", presets)

        val effects = JSONArray()
        LedAnimationType.values().forEach { effect ->
            val need = ExternalCapture.needOf(effect)
            effects.put(JSONObject().apply {
                put("name", effect.name)
                put("capture", need.name)
                put("allowed", need == ExternalCapture.Need.NONE || ExternalApiGate.allowsPower(
                    ExternalApiGate.Power.Effect(effect.name), input.privilegedEnabled, verified, input.grant))
                put("available", available(effect))
            })
        }
        out.put("effects", effects)

        out.put("functions", JSONArray(ExternalFunction.values().map { it.wireName }))
        return out
    }
}
