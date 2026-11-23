package com.moonbench.bifrost.external

import android.content.Context
import android.content.SharedPreferences
import com.moonbench.bifrost.plugins.ExternalGrant

/**
 * Single gate for the external API.
 *
 * Two user switches, both OFF by default:
 *  - "Allow third-party LED control" ([PREF_ENABLED]) — the master switch.
 *    Nothing but ACTION_QUERY_PLUGIN is accepted while it is off.
 *  - "Let apps start presets and reactive modes" ([PREF_PRIVILEGED_ENABLED]) —
 *    the v2 powers (ACTION_APPLY_PRESET, capture-driven effects in
 *    ACTION_DISPLAY) for ANY permitted caller. Has no effect while the master
 *    switch is off.
 *
 * A plugin installed from the Plugin Store may instead grant those powers to
 * its own companion app only ([ExternalGrant]); that needs the master switch
 * and a caller identity Android vouched for.
 */
object ExternalApiGate {

    const val PREFS_NAME = "bifrost_prefs"
    const val PREF_ENABLED = "external_api_enabled"
    const val PREF_PRIVILEGED_ENABLED = "external_api_privileged_enabled"

    fun isMasterEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_ENABLED, false)

    fun isPrivilegedEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_PRIVILEGED_ENABLED, false)

    fun isAllowed(context: Context, @Suppress("UNUSED_PARAMETER") callerPackage: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return isMasterEnabled(prefs)
    }

    /** What a privileged request asks for. */
    sealed class Power {
        data class Preset(val name: String) : Power()
        data class Effect(val name: String) : Power()
    }

    /**
     * Pure decision for a v2 privileged request. The master switch is checked
     * by the caller of this function (it gates every command).
     *
     * @param privilegedEnabled the global "presets and reactive modes" switch.
     * @param verified the caller's package was vouched for by Android.
     * @param grant the plugin grant for the caller's package, if any.
     */
    fun allowsPower(
        power: Power,
        privilegedEnabled: Boolean,
        verified: Boolean,
        grant: ExternalGrant?,
    ): Boolean {
        if (privilegedEnabled) return true
        if (!verified || grant == null) return false
        return when (power) {
            is Power.Preset -> grant.allowsPreset(power.name)
            is Power.Effect -> grant.allowsEffect(power.name)
        }
    }
}
