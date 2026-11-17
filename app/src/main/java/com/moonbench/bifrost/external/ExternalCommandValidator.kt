package com.moonbench.bifrost.external

import android.content.Intent
import com.moonbench.bifrost.animations.LedAnimationType

object ExternalCommandValidator {

    enum class ErrorKind {
        UnsupportedVersion,
        UnknownEffect,
        EffectRequiresProjection,
        ConflictingTerminators,
        DurationOutOfRange,
        UnknownUntilValue,
        MissingProfileName,
        UnknownAction,
        MissingPresetName,
        UnknownFunction,
    }

    class ValidationException(val kind: ErrorKind) : RuntimeException(kind.name)

    /** True for every protocol version this build accepts. */
    fun isSupportedVersion(apiVersion: Int): Boolean =
        apiVersion in ExternalApi.MIN_API_VERSION..ExternalApi.API_VERSION

    fun validate(intent: Intent, callerPackage: String): Result<ExternalApiCommand> {
        val apiVersion = intent.getIntExtra(ExternalApi.EXTRA_API_VERSION, ExternalApi.MIN_API_VERSION)
        if (!isSupportedVersion(apiVersion)) {
            return Result.failure(ValidationException(ErrorKind.UnsupportedVersion))
        }

        val requestId = intent.getStringExtra(ExternalApi.EXTRA_REQUEST_ID)
            ?.take(ExternalApi.MAX_REQUEST_ID_LENGTH)

        return when (intent.action) {
            ExternalApi.ACTION_DISPLAY -> parseDisplay(intent, callerPackage, requestId, apiVersion)
            ExternalApi.ACTION_CLEAR -> Result.success(
                ExternalApiCommand.Clear(callerPackage, requestId, apiVersion)
            )
            ExternalApi.ACTION_INSTALL_PROFILE -> parseInstall(intent, callerPackage, requestId)
            ExternalApi.ACTION_UNINSTALL_PROFILE -> parseUninstall(intent, callerPackage, requestId)
            // v2 verbs: a v1 caller doesn't know them, so treat them as unknown there.
            ExternalApi.ACTION_APPLY_PRESET ->
                if (apiVersion < 2) Result.failure(ValidationException(ErrorKind.UnsupportedVersion))
                else parseApplyPreset(intent, callerPackage, requestId, apiVersion)
            ExternalApi.ACTION_INVOKE ->
                if (apiVersion < 2) Result.failure(ValidationException(ErrorKind.UnsupportedVersion))
                else parseInvoke(intent, callerPackage, requestId, apiVersion)
            else -> Result.failure(ValidationException(ErrorKind.UnknownAction))
        }
    }

    /**
     * Parse the effect name. The capture-driven effects stay refused for v1
     * callers (unchanged contract, result -3); a v2 caller gets them through
     * and the receiver decides by permission and capture state.
     */
    private fun parseEffect(intent: Intent, allowReactive: Boolean): Result<LedAnimationType> {
        val effectName = intent.getStringExtra(ExternalApi.EXTRA_EFFECT)
            ?: LedAnimationType.STATIC.name
        val effect = runCatching { LedAnimationType.valueOf(effectName) }.getOrNull()
            ?: return Result.failure(ValidationException(ErrorKind.UnknownEffect))
        if (!allowReactive && ExternalCapture.isReactive(effect)) {
            return Result.failure(ValidationException(ErrorKind.EffectRequiresProjection))
        }
        return Result.success(effect)
    }

    private fun resolveIntensity(intent: Intent): Int {
        val scale = intent.getIntExtra(ExternalApi.EXTRA_INTENSITY_SCALE, 255).coerceIn(1, 255)
        val raw = intent.getIntExtra(ExternalApi.EXTRA_INTENSITY, 255)
        return if (scale == 255) {
            raw.coerceIn(0, 255)
        } else {
            ((raw.coerceIn(0, scale).toFloat() / scale) * 255f).toInt().coerceIn(0, 255)
        }
    }

    private fun parseTerminator(intent: Intent): Result<Terminator> {
        val hasDuration = intent.hasExtra(ExternalApi.EXTRA_DURATION_MS)
        val hasUntil = intent.hasExtra(ExternalApi.EXTRA_UNTIL)
        val isIndefinite = intent.getBooleanExtra(ExternalApi.EXTRA_INDEFINITE, false)

        val declaredTerminators = listOf(hasDuration, hasUntil, isIndefinite).count { it }
        if (declaredTerminators > 1) {
            return Result.failure(ValidationException(ErrorKind.ConflictingTerminators))
        }

        val terminator: Terminator = when {
            hasDuration -> {
                val ms = intent.getLongExtra(ExternalApi.EXTRA_DURATION_MS, -1L)
                if (ms < 1L || ms > ExternalApi.MAX_DURATION_MS) {
                    return Result.failure(ValidationException(ErrorKind.DurationOutOfRange))
                }
                Terminator.Duration(ms)
            }
            isIndefinite -> Terminator.UntilExplicitClear
            hasUntil -> when (intent.getStringExtra(ExternalApi.EXTRA_UNTIL)) {
                ExternalApi.UNTIL_NEXT_COMMAND -> Terminator.UntilNextCommand
                ExternalApi.UNTIL_EXPLICIT_CLEAR -> Terminator.UntilExplicitClear
                else -> return Result.failure(ValidationException(ErrorKind.UnknownUntilValue))
            }
            else -> Terminator.UntilNextCommand
        }
        return Result.success(terminator)
    }

    private fun parsePriority(intent: Intent): Int =
        intent.getIntExtra(ExternalApi.EXTRA_PRIORITY, 50).coerceIn(0, 100)

    private fun parseDisplay(
        intent: Intent,
        callerPackage: String,
        requestId: String?,
        apiVersion: Int,
    ): Result<ExternalApiCommand> {
        val effect = parseEffect(intent, allowReactive = apiVersion >= 2)
            .getOrElse { return Result.failure(it) }
        val terminator = parseTerminator(intent).getOrElse { return Result.failure(it) }

        val color = intent.getIntExtra(ExternalApi.EXTRA_COLOR, 0)
        return Result.success(
            ExternalApiCommand.Display(
                callerPackage = callerPackage,
                requestId = requestId,
                effect = effect,
                color = color,
                colorRight = intent.getIntExtra(ExternalApi.EXTRA_COLOR_RIGHT, color),
                intensity = resolveIntensity(intent),
                speed = intent.getFloatExtra(ExternalApi.EXTRA_SPEED, 0.5f).coerceIn(0f, 1f),
                smoothness = intent.getFloatExtra(ExternalApi.EXTRA_SMOOTHNESS, 0.5f).coerceIn(0f, 1f),
                sensitivity = intent.getFloatExtra(ExternalApi.EXTRA_SENSITIVITY, 0.5f).coerceIn(0f, 1f),
                terminator = terminator,
                priority = parsePriority(intent),
                phaseSeconds = intent.getFloatExtra(ExternalApi.EXTRA_PHASE_SECONDS, 0f),
                flickering = intent.getBooleanExtra(ExternalApi.EXTRA_FLICKERING, false),
                burstWallMs = intent.getLongExtra(ExternalApi.EXTRA_BURST_WALL_MS, 0L),
                apiVersion = apiVersion,
            )
        )
    }

    private fun parseApplyPreset(
        intent: Intent,
        callerPackage: String,
        requestId: String?,
        apiVersion: Int,
    ): Result<ExternalApiCommand> {
        val presetName = intent.getStringExtra(ExternalApi.EXTRA_PRESET_NAME)?.trim()
        if (presetName.isNullOrEmpty()) {
            return Result.failure(ValidationException(ErrorKind.MissingPresetName))
        }
        val terminator = parseTerminator(intent).getOrElse { return Result.failure(it) }
        return Result.success(
            ExternalApiCommand.ApplyPreset(
                callerPackage = callerPackage,
                requestId = requestId,
                presetName = presetName,
                terminator = terminator,
                priority = parsePriority(intent),
                intensity = if (intent.hasExtra(ExternalApi.EXTRA_INTENSITY)) resolveIntensity(intent) else null,
                apiVersion = apiVersion,
            )
        )
    }

    private fun parseInvoke(
        intent: Intent,
        callerPackage: String,
        requestId: String?,
        apiVersion: Int,
    ): Result<ExternalApiCommand> {
        val function = ExternalFunction.fromWire(intent.getStringExtra(ExternalApi.EXTRA_FUNCTION))
            ?: return Result.failure(ValidationException(ErrorKind.UnknownFunction))
        val terminator = parseTerminator(intent).getOrElse { return Result.failure(it) }
        val pulseKind = when (intent.getStringExtra(ExternalApi.EXTRA_PULSE_KIND)) {
            ExternalApi.PULSE_KIND_STATIC -> ExternalApi.PULSE_KIND_STATIC
            else -> ExternalApi.PULSE_KIND_PULSE
        }
        return Result.success(
            ExternalApiCommand.Invoke(
                callerPackage = callerPackage,
                requestId = requestId,
                function = function,
                intensity = resolveIntensity(intent),
                pulseKind = pulseKind,
                terminator = terminator,
                priority = parsePriority(intent),
                apiVersion = apiVersion,
            )
        )
    }

    private fun parseInstall(
        intent: Intent,
        callerPackage: String,
        requestId: String?
    ): Result<ExternalApiCommand> {
        val profileName = intent.getStringExtra(ExternalApi.EXTRA_PROFILE_NAME)?.trim()
        if (profileName.isNullOrEmpty()) {
            return Result.failure(ValidationException(ErrorKind.MissingProfileName))
        }

        // Installing stays limited to the non-capture effects in every version.
        val effect = parseEffect(intent, allowReactive = false).getOrElse { return Result.failure(it) }
        val color = intent.getIntExtra(ExternalApi.EXTRA_COLOR, 0)

        return Result.success(
            ExternalApiCommand.InstallProfile(
                callerPackage = callerPackage,
                requestId = requestId,
                profileName = profileName,
                effect = effect,
                color = color,
                colorRight = intent.getIntExtra(ExternalApi.EXTRA_COLOR_RIGHT, color),
                intensity = resolveIntensity(intent),
                speed = intent.getFloatExtra(ExternalApi.EXTRA_SPEED, 0.5f).coerceIn(0f, 1f),
                smoothness = intent.getFloatExtra(ExternalApi.EXTRA_SMOOTHNESS, 0.5f).coerceIn(0f, 1f),
                sensitivity = intent.getFloatExtra(ExternalApi.EXTRA_SENSITIVITY, 0.5f).coerceIn(0f, 1f),
                saturationBoost = intent.getFloatExtra(ExternalApi.EXTRA_SATURATION_BOOST, 0f).coerceIn(0f, 1f),
                useCustomSampling = intent.getBooleanExtra(ExternalApi.EXTRA_USE_CUSTOM_SAMPLING, false),
                useSingleColor = intent.getBooleanExtra(ExternalApi.EXTRA_USE_SINGLE_COLOR, false),
                breatheWhenCharging = intent.getBooleanExtra(ExternalApi.EXTRA_BREATHE_WHEN_CHARGING, false),
                indicateChargingSpeed = intent.getBooleanExtra(ExternalApi.EXTRA_INDICATE_CHARGING_SPEED, false),
                flashWhenReady = intent.getBooleanExtra(ExternalApi.EXTRA_FLASH_WHEN_READY, false),
                batteryLowColor = optColor(intent, ExternalApi.EXTRA_BATTERY_LOW_COLOR),
                batteryMidColor = optColor(intent, ExternalApi.EXTRA_BATTERY_MID_COLOR),
                batteryHighColor = optColor(intent, ExternalApi.EXTRA_BATTERY_HIGH_COLOR),
                cpuCoolColor = optColor(intent, ExternalApi.EXTRA_CPU_COOL_COLOR),
                cpuWarmColor = optColor(intent, ExternalApi.EXTRA_CPU_WARM_COLOR),
                cpuHotColor = optColor(intent, ExternalApi.EXTRA_CPU_HOT_COLOR),
                replaceIfExists = intent.getBooleanExtra(ExternalApi.EXTRA_PROFILE_REPLACE_IF_EXISTS, false),
            )
        )
    }

    private fun parseUninstall(
        intent: Intent,
        callerPackage: String,
        requestId: String?
    ): Result<ExternalApiCommand> {
        val profileName = intent.getStringExtra(ExternalApi.EXTRA_PROFILE_NAME)?.trim()
        if (profileName.isNullOrEmpty()) {
            return Result.failure(ValidationException(ErrorKind.MissingProfileName))
        }
        return Result.success(
            ExternalApiCommand.UninstallProfile(
                callerPackage = callerPackage,
                requestId = requestId,
                profileName = profileName,
            )
        )
    }

    private fun optColor(intent: Intent, key: String): Int? =
        if (intent.hasExtra(key)) intent.getIntExtra(key, 0) else null
}
