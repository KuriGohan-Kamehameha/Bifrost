package com.moonbench.bifrost.external

/**
 * Public contract for third-party apps that want Bifrost to display LED state
 * on their behalf. Copy these constants into the caller app (or depend on this
 * file directly) — nothing else under `external/` is part of the stable API.
 */
object ExternalApi {

    const val PERMISSION = "com.moonbench.bifrost.permission.CONTROL_LEDS"

    const val ACTION_DISPLAY = "com.moonbench.bifrost.api.ACTION_DISPLAY"
    const val ACTION_CLEAR = "com.moonbench.bifrost.api.ACTION_CLEAR"
    const val ACTION_INSTALL_PROFILE = "com.moonbench.bifrost.api.ACTION_INSTALL_PROFILE"
    const val ACTION_UNINSTALL_PROFILE = "com.moonbench.bifrost.api.ACTION_UNINSTALL_PROFILE"
    const val ACTION_QUERY_PLUGIN = "com.moonbench.bifrost.api.ACTION_QUERY_PLUGIN"

    // Lightweight transient trigger — modulates the running effect in place
    // (a brief pulse or static scramble) without replacing the override.
    // Gate-checked but not rate-limited, so rapid menu navigation lands every
    // time. Currently consumed by the PIPBOY effect (triggerPulse/Static).
    const val ACTION_PULSE = "com.moonbench.bifrost.api.ACTION_PULSE"
    const val EXTRA_PULSE_KIND = "pulseKind"
    const val PULSE_KIND_PULSE = "PULSE"
    const val PULSE_KIND_STATIC = "STATIC"

    // ── API v2 (additive; v1 callers are served exactly as before) ──────────
    // Apply a saved preset by name as a removable override (same lease,
    // priority and revert rules as ACTION_DISPLAY). Privileged: see below.
    const val ACTION_APPLY_PRESET = "com.moonbench.bifrost.api.ACTION_APPLY_PRESET"
    // Small verbs on the caller's own override (EXTRA_FUNCTION).
    const val ACTION_INVOKE = "com.moonbench.bifrost.api.ACTION_INVOKE"
    // Ordered broadcast: resultData is a JSON description of what this caller
    // may use (presets, effects, functions, capture state). Read-only.
    const val ACTION_QUERY = "com.moonbench.bifrost.api.ACTION_QUERY"

    /** Highest protocol version this build speaks. */
    const val API_VERSION = 2
    /** Oldest protocol version still accepted (v1 behaviour is unchanged). */
    const val MIN_API_VERSION = 1

    const val EXTRA_API_VERSION = "apiVersion"
    const val EXTRA_EFFECT = "effect"
    const val EXTRA_COLOR = "color"
    const val EXTRA_COLOR_RIGHT = "colorRight"
    const val EXTRA_INTENSITY = "intensity"
    const val EXTRA_INTENSITY_SCALE = "intensityScale"
    const val EXTRA_SPEED = "speed"
    const val EXTRA_SMOOTHNESS = "smoothness"
    const val EXTRA_SENSITIVITY = "sensitivity"
    const val EXTRA_DURATION_MS = "durationMs"
    const val EXTRA_UNTIL = "until"
    const val EXTRA_INDEFINITE = "indefinite"
    const val EXTRA_PRIORITY = "priority"
    const val EXTRA_REQUEST_ID = "requestId"
    // Optional: caller's elapsed effect-clock seconds, used by deterministic
    // effects (e.g. PIPBOY) to phase-align their animation with the caller.
    const val EXTRA_PHASE_SECONDS = "phaseSeconds"
    // Optional: current screen flicker state (PIPBOY shimmers its LEDs when true).
    const val EXTRA_FLICKERING = "flickering"
    // Optional: elapsedRealtime() ms of the caller's most recent burst flash.
    const val EXTRA_BURST_WALL_MS = "burstWallMs"

    const val EXTRA_PROFILE_NAME = "profileName"
    const val EXTRA_PLUGIN_ID = "pluginId"
    const val EXTRA_PROFILE_REPLACE_IF_EXISTS = "replaceIfExists"
    const val EXTRA_SATURATION_BOOST = "saturationBoost"
    const val EXTRA_USE_CUSTOM_SAMPLING = "useCustomSampling"
    const val EXTRA_USE_SINGLE_COLOR = "useSingleColor"
    const val EXTRA_BREATHE_WHEN_CHARGING = "breatheWhenCharging"
    const val EXTRA_INDICATE_CHARGING_SPEED = "indicateChargingSpeed"
    const val EXTRA_FLASH_WHEN_READY = "flashWhenReady"
    const val EXTRA_BATTERY_LOW_COLOR = "batteryLowColor"
    const val EXTRA_BATTERY_MID_COLOR = "batteryMidColor"
    const val EXTRA_BATTERY_HIGH_COLOR = "batteryHighColor"
    const val EXTRA_CPU_COOL_COLOR = "cpuCoolColor"
    const val EXTRA_CPU_WARM_COLOR = "cpuWarmColor"
    const val EXTRA_CPU_HOT_COLOR = "cpuHotColor"

    // v2: who is calling. A PendingIntent the caller created (any target, e.g.
    // PendingIntent.getBroadcast(ctx, 0, Intent(pkg + ".BIFROST"), FLAG_IMMUTABLE));
    // Bifrost reads only its creator package, which Android vouches for. A plain
    // broadcast's sender can't be identified inside a receiver, so per-app grants
    // (plugin `externalAccess`) need this, or Android 14 share-identity.
    const val EXTRA_CALLER_TOKEN = "callerToken"
    // v2: ACTION_APPLY_PRESET — the preset's exact name (as listed by ACTION_QUERY).
    const val EXTRA_PRESET_NAME = "presetName"
    // v2: ACTION_INVOKE — one of FUNCTION_*.
    const val EXTRA_FUNCTION = "function"

    const val FUNCTION_BRIGHTNESS = "BRIGHTNESS"   // EXTRA_INTENSITY (+scale) on your active override
    const val FUNCTION_PULSE = "PULSE"             // same as ACTION_PULSE (EXTRA_PULSE_KIND), rate limited
    const val FUNCTION_PAUSE = "PAUSE"             // LEDs dark as an override (terminator + priority apply)
    const val FUNCTION_RESUME = "RESUME"           // same as ACTION_CLEAR

    const val UNTIL_NEXT_COMMAND = "NEXT_COMMAND"
    const val UNTIL_EXPLICIT_CLEAR = "EXPLICIT_CLEAR"

    const val RESULT_ACCEPTED = 0
    const val RESULT_REJECTED_DISABLED = -1
    const val RESULT_REJECTED_VERSION = -2
    const val RESULT_REJECTED_VALIDATION = -3
    const val RESULT_REJECTED_UNAUTHORIZED = -4
    const val RESULT_REJECTED_RATE_LIMITED = -5
    const val RESULT_REJECTED_UNKNOWN_ACTION = -6
    const val RESULT_NOT_FOUND = 1
    // v2 only (a v1 caller still gets the v1 codes):
    /** The user hasn't allowed this caller to start presets / reactive modes. */
    const val RESULT_REJECTED_NOT_PERMITTED = -7
    /** Reactive mode needs screen/audio capture that Bifrost doesn't hold right now. */
    const val RESULT_REJECTED_CAPTURE_UNAVAILABLE = -8
    /** Bifrost's LED service isn't running, so nothing was shown. */
    const val RESULT_REJECTED_SERVICE_NOT_RUNNING = -9

    const val MAX_DURATION_MS = 600_000L
    const val MAX_REQUEST_ID_LENGTH = 64
}
