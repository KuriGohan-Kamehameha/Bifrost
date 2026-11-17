package com.moonbench.bifrost.external

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.plugins.ExternalGrantStore
import com.moonbench.bifrost.plugins.PluginPrefs
import com.moonbench.bifrost.services.AppProfileManager
import com.moonbench.bifrost.services.BifrostAccessibilityService
import com.moonbench.bifrost.services.LEDService

class ExternalApiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BIBI/ExtApi"
    }

    /**
     * Who sent the broadcast. [verified] is true only when Android vouched for
     * [pkg] (share-identity on Android 14+, or the creator of a PendingIntent the
     * caller attached). Otherwise [pkg] is the legacy best guess, which inside a
     * receiver is Bifrost's own package for every sender (onReceive does not run
     * in the sender's Binder call), so it can't key per-app grants.
     */
    private data class Caller(val pkg: String, val verified: Boolean)

    override fun onReceive(context: Context, intent: Intent) {
        try {
            handleSafely(context, intent)
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive: unhandled error", t)
            setResult(ExternalApi.RESULT_REJECTED_VALIDATION, null)
        }
    }

    private fun handleSafely(context: Context, intent: Intent) {
        val callerUid = Binder.getCallingUid()
        val caller = resolveCaller(context, intent, callerUid)
        if (caller == null) {
            setResult(ExternalApi.RESULT_REJECTED_UNAUTHORIZED, null)
            return
        }
        val callerPackage = caller.pkg

        if (intent.action == ExternalApi.ACTION_QUERY_PLUGIN) {
            handlePluginQuery(context, intent)
            return
        }

        if (intent.action == ExternalApi.ACTION_QUERY) {
            handleQuery(context, intent, caller, callerUid)
            return
        }

        if (!ExternalApiGate.isAllowed(context, callerPackage)) {
            setResult(ExternalApi.RESULT_REJECTED_DISABLED, null)
            return
        }

        // Transient trigger fast-path — gate-checked but NOT rate-limited or
        // validated. It's a cheap in-place modulation of the running effect;
        // rate-limiting it would defeat the point (rapid menu navigation).
        if (intent.action == ExternalApi.ACTION_PULSE) {
            val kind = intent.getStringExtra(ExternalApi.EXTRA_PULSE_KIND)
                ?: ExternalApi.PULSE_KIND_PULSE
            forwardToService(context, LEDService.ACTION_EXTERNAL_PULSE) {
                putExtra(LEDService.EXTRA_EXTERNAL_PULSE_KIND, kind)
            }
            setResult(ExternalApi.RESULT_ACCEPTED, null)
            return
        }

        if (!ExternalApiRateLimiter.allow(rateKey(caller, callerUid))) {
            setResult(ExternalApi.RESULT_REJECTED_RATE_LIMITED, null)
            return
        }

        val validated = ExternalCommandValidator.validate(intent, callerPackage)
        val cmd = validated.getOrElse { err ->
            val code = when ((err as? ExternalCommandValidator.ValidationException)?.kind) {
                ExternalCommandValidator.ErrorKind.UnsupportedVersion -> ExternalApi.RESULT_REJECTED_VERSION
                ExternalCommandValidator.ErrorKind.UnknownAction -> ExternalApi.RESULT_REJECTED_UNKNOWN_ACTION
                else -> ExternalApi.RESULT_REJECTED_VALIDATION
            }
            Log.d(TAG, "handle: rejected $callerPackage: ${err.message}")
            setResult(code, null)
            return
        }

        val prefs = context.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)
        val denial = authorize(context, prefs, cmd, caller)
        if (denial != null) {
            Log.d(TAG, "handle: $callerPackage refused ($denial) for ${cmd::class.java.simpleName}")
            setResult(denial, null)
            return
        }

        // v1 contract: a command for a stopped service is accepted and dropped.
        // v2 callers are told, so they can stop retrying.
        if (cmd.apiVersion >= 2 && needsService(cmd) && !LEDService.isRunning) {
            setResult(ExternalApi.RESULT_REJECTED_SERVICE_NOT_RUNNING, null)
            return
        }

        val code = dispatch(context, prefs, cmd)
        setResult(code, if (code == ExternalApi.RESULT_ACCEPTED) cmd.requestId else null)
    }

    /**
     * Permission + feasibility for the v2 powers. Returns a result code to
     * refuse with, or null to proceed. Non-privileged commands pass straight
     * through (their v1 rules are unchanged).
     */
    private fun authorize(context: Context, prefs: SharedPreferences, cmd: ExternalApiCommand, caller: Caller): Int? {
        val power = when (cmd) {
            is ExternalApiCommand.ApplyPreset -> ExternalApiGate.Power.Preset(cmd.presetName)
            is ExternalApiCommand.Display ->
                if (ExternalCapture.isReactive(cmd.effect)) ExternalApiGate.Power.Effect(cmd.effect.name)
                else return null
            else -> return null
        }
        val grant = if (caller.verified) ExternalGrantStore.forPackage(prefs, caller.pkg) else null
        if (!ExternalApiGate.allowsPower(power, ExternalApiGate.isPrivilegedEnabled(prefs), caller.verified, grant)) {
            return ExternalApi.RESULT_REJECTED_NOT_PERMITTED
        }
        val effect = when (cmd) {
            is ExternalApiCommand.ApplyPreset ->
                AppProfileManager(prefs).loadPresetByName(cmd.presetName)?.animationType
                    ?: return ExternalApi.RESULT_NOT_FOUND
            is ExternalApiCommand.Display -> cmd.effect
            else -> return null
        }
        if (!isCaptureAvailable(context, prefs, effect)) return ExternalApi.RESULT_REJECTED_CAPTURE_UNAVAILABLE
        return null
    }

    private fun isCaptureAvailable(context: Context, prefs: SharedPreferences, effect: LedAnimationType): Boolean =
        ExternalCapture.isAvailable(
            effect,
            LEDService.hasProjectionCapture,
            hasAudioPermission(context),
            BifrostAccessibilityService.instance != null,
            prefs.getBoolean(
                LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION,
                LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION
            )
        )

    private fun hasAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun needsService(cmd: ExternalApiCommand): Boolean = when (cmd) {
        is ExternalApiCommand.Display,
        is ExternalApiCommand.Clear,
        is ExternalApiCommand.ApplyPreset,
        is ExternalApiCommand.Invoke -> true
        is ExternalApiCommand.InstallProfile,
        is ExternalApiCommand.UninstallProfile -> false
    }

    private fun dispatch(context: Context, prefs: SharedPreferences, cmd: ExternalApiCommand): Int {
        when (cmd) {
            is ExternalApiCommand.Display -> forwardDisplay(context, cmd)
            is ExternalApiCommand.Clear -> forwardClear(context, cmd.callerPackage)
            is ExternalApiCommand.InstallProfile -> ExternalProfileStore.installManagedPreset(prefs, cmd)
            is ExternalApiCommand.UninstallProfile ->
                ExternalProfileStore.uninstallManagedPreset(prefs, cmd.callerPackage, cmd.profileName)
            is ExternalApiCommand.ApplyPreset ->
                forwardToService(context, LEDService.ACTION_EXTERNAL_APPLY_PRESET) {
                    putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, cmd.callerPackage)
                    putExtra(LEDService.EXTRA_EXTERNAL_PRESET_NAME, cmd.presetName)
                    putExtra(LEDService.EXTRA_EXTERNAL_PRIORITY, cmd.priority)
                    putExtra(LEDService.EXTRA_EXTERNAL_INTENSITY, cmd.intensity ?: -1)
                    putTerminator(cmd.terminator)
                }
            is ExternalApiCommand.Invoke -> return dispatchInvoke(context, cmd)
        }
        return ExternalApi.RESULT_ACCEPTED
    }

    private fun dispatchInvoke(context: Context, cmd: ExternalApiCommand.Invoke): Int {
        when (cmd.function) {
            ExternalFunction.BRIGHTNESS -> {
                // Only the owner of the running override may retune it.
                if (LEDService.activeExternalOwner != cmd.callerPackage) return ExternalApi.RESULT_NOT_FOUND
                forwardToService(context, LEDService.ACTION_EXTERNAL_BRIGHTNESS) {
                    putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, cmd.callerPackage)
                    putExtra(LEDService.EXTRA_EXTERNAL_INTENSITY, cmd.intensity)
                }
            }
            ExternalFunction.PULSE -> forwardToService(context, LEDService.ACTION_EXTERNAL_PULSE) {
                putExtra(LEDService.EXTRA_EXTERNAL_PULSE_KIND, cmd.pulseKind)
            }
            // LEDs dark as an ordinary override, so priority, lease and revert
            // all behave exactly as for ACTION_DISPLAY.
            ExternalFunction.PAUSE -> forwardDisplay(
                context,
                ExternalApiCommand.Display(
                    callerPackage = cmd.callerPackage,
                    requestId = cmd.requestId,
                    effect = LedAnimationType.STATIC,
                    color = 0,
                    colorRight = 0,
                    intensity = 0,
                    speed = 0.5f,
                    smoothness = 0.5f,
                    sensitivity = 0.5f,
                    terminator = cmd.terminator,
                    priority = cmd.priority,
                    apiVersion = cmd.apiVersion,
                )
            )
            ExternalFunction.RESUME -> forwardClear(context, cmd.callerPackage)
        }
        return ExternalApi.RESULT_ACCEPTED
    }

    private fun forwardDisplay(context: Context, cmd: ExternalApiCommand.Display) =
        forwardToService(context, LEDService.ACTION_EXTERNAL_DISPLAY) {
            putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, cmd.callerPackage)
            putExtra(LEDService.EXTRA_EXTERNAL_EFFECT, cmd.effect.name)
            putExtra(LEDService.EXTRA_EXTERNAL_COLOR, cmd.color)
            putExtra(LEDService.EXTRA_EXTERNAL_COLOR_RIGHT, cmd.colorRight)
            putExtra(LEDService.EXTRA_EXTERNAL_INTENSITY, cmd.intensity)
            putExtra(LEDService.EXTRA_EXTERNAL_SPEED, cmd.speed)
            putExtra(LEDService.EXTRA_EXTERNAL_SMOOTHNESS, cmd.smoothness)
            putExtra(LEDService.EXTRA_EXTERNAL_SENSITIVITY, cmd.sensitivity)
            putExtra(LEDService.EXTRA_EXTERNAL_PRIORITY, cmd.priority)
            putExtra(LEDService.EXTRA_EXTERNAL_PHASE_SECONDS, cmd.phaseSeconds)
            putExtra(LEDService.EXTRA_EXTERNAL_FLICKERING, cmd.flickering)
            putExtra(LEDService.EXTRA_EXTERNAL_BURST_WALL_MS, cmd.burstWallMs)
            putTerminator(cmd.terminator)
        }

    private fun forwardClear(context: Context, callerPackage: String) =
        forwardToService(context, LEDService.ACTION_EXTERNAL_CLEAR) {
            putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, callerPackage)
        }

    private fun Intent.putTerminator(t: Terminator) {
        when (t) {
            is Terminator.Duration -> {
                putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_DURATION)
                putExtra(LEDService.EXTRA_EXTERNAL_DURATION_MS, t.millis)
            }
            Terminator.UntilNextCommand ->
                putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_NEXT_COMMAND)
            Terminator.UntilExplicitClear ->
                putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_EXPLICIT_CLEAR)
        }
    }

    private fun handlePluginQuery(context: Context, intent: Intent) {
        val apiVersion = intent.getIntExtra(ExternalApi.EXTRA_API_VERSION, ExternalApi.MIN_API_VERSION)
        if (!ExternalCommandValidator.isSupportedVersion(apiVersion)) {
            setResult(ExternalApi.RESULT_REJECTED_VERSION, null)
            return
        }

        val pluginId = intent.getStringExtra(ExternalApi.EXTRA_PLUGIN_ID)?.trim()
        if (pluginId.isNullOrEmpty()) {
            setResult(ExternalApi.RESULT_REJECTED_VALIDATION, null)
            return
        }

        val installedVersion = PluginPrefs.installedVersion(PluginPrefs.prefs(context), pluginId)
        if (installedVersion != null && installedVersion > 0) {
            setResult(ExternalApi.RESULT_ACCEPTED, "$pluginId:$installedVersion")
        } else {
            setResult(ExternalApi.RESULT_NOT_FOUND, pluginId)
        }
    }

    /**
     * v2 ACTION_QUERY: what this caller may use, as JSON in resultData. With
     * the master switch off the reply is RESULT_REJECTED_DISABLED carrying only
     * the version fields, so a caller can still feature-detect.
     */
    private fun handleQuery(context: Context, intent: Intent, caller: Caller, callerUid: Int) {
        val apiVersion = intent.getIntExtra(ExternalApi.EXTRA_API_VERSION, ExternalApi.MIN_API_VERSION)
        if (!ExternalCommandValidator.isSupportedVersion(apiVersion)) {
            setResult(ExternalApi.RESULT_REJECTED_VERSION, null)
            return
        }
        if (!ExternalApiRateLimiter.allow(rateKey(caller, callerUid))) {
            setResult(ExternalApi.RESULT_REJECTED_RATE_LIMITED, null)
            return
        }
        val prefs = context.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = ExternalApiGate.isMasterEnabled(prefs)
        val verifiedCaller = caller.pkg.takeIf { caller.verified }
        val answer = ExternalQuery.build(
            ExternalQuery.Inputs(
                enabled = enabled,
                serviceRunning = LEDService.isRunning,
                verifiedCaller = verifiedCaller,
                privilegedEnabled = ExternalApiGate.isPrivilegedEnabled(prefs),
                grant = verifiedCaller?.let { ExternalGrantStore.forPackage(prefs, it) },
                hasProjection = LEDService.hasProjectionCapture,
                audioPermitted = hasAudioPermission(context),
                accessibilityRunning = BifrostAccessibilityService.instance != null,
                ambientPrefersProjection = prefs.getBoolean(
                    LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION,
                    LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION
                ),
                presetsJson = if (enabled) prefs.getString("presets_json", null) else null,
            )
        )
        setResult(
            if (enabled) ExternalApi.RESULT_ACCEPTED else ExternalApi.RESULT_REJECTED_DISABLED,
            answer.toString()
        )
    }

    /**
     * Only forward Display/Clear intents if the service is already live. External
     * callers can't start a foreground service from the background on Android 12+,
     * and silently dropping is less surprising than throwing SecurityException
     * back to the caller's process (they didn't cause it).
     */
    private fun forwardToService(
        context: Context,
        action: String,
        fill: Intent.() -> Unit,
    ) {
        if (!LEDService.isRunning) {
            Log.d(TAG, "forwardToService: service not running, dropping $action")
            return
        }
        val svc = Intent(context, LEDService::class.java).apply {
            this.action = action
            fill()
        }
        runCatching { context.startService(svc) }
            .onFailure { Log.w(TAG, "forwardToService: startService failed", it) }
    }

    /**
     * Rate-limit bucket: a verified caller gets its own; everything else shares
     * the receiver-side uid bucket as before (unchanged v1 behaviour).
     */
    private fun rateKey(caller: Caller, uid: Int): String =
        if (caller.verified) "pkg:${caller.pkg}" else "uid:$uid"

    private fun resolveCaller(context: Context, intent: Intent, uid: Int): Caller? {
        verifiedPackage(intent)?.let { return Caller(it, verified = true) }
        val legacy = resolveCallerPackage(context, uid) ?: return null
        return Caller(legacy, verified = false)
    }

    /** The sender's package if Android vouches for it, else null. */
    private fun verifiedPackage(intent: Intent): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sentFromPackage?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val token = runCatching {
            intent.getParcelableExtra(ExternalApi.EXTRA_CALLER_TOKEN, PendingIntent::class.java)
        }.getOrNull() ?: return null
        return token.creatorPackage?.takeIf { it.isNotBlank() }
    }

    private fun resolveCallerPackage(context: Context, uid: Int): String? {
        if (uid <= 0) return null
        return runCatching {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
    }

    private fun setResult(code: Int, data: String?) {
        if (isOrderedBroadcast) {
            resultCode = code
            resultData = data
        }
    }
}

/**
 * Per-caller token bucket — 8 burst, 4/sec sustained. Lives in-process for the
 * life of Bifrost; cleared with process death. Memory use is negligible (one
 * entry per caller key ever seen; see ExternalApiReceiver.rateKey).
 */
private object ExternalApiRateLimiter {

    private const val BURST = 8
    private const val SUSTAINED_PER_SECOND = 4f

    private data class Bucket(var tokens: Float, var lastRefillMs: Long)

    private val buckets = HashMap<String, Bucket>()

    @Synchronized
    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val bucket = buckets.getOrPut(key) { Bucket(BURST.toFloat(), now) }
        val elapsedSec = (now - bucket.lastRefillMs) / 1000f
        bucket.tokens = (bucket.tokens + elapsedSec * SUSTAINED_PER_SECOND)
            .coerceAtMost(BURST.toFloat())
        bucket.lastRefillMs = now
        return if (bucket.tokens >= 1f) {
            bucket.tokens -= 1f
            true
        } else {
            false
        }
    }
}
