package com.moonbench.bifrost.external

import com.moonbench.bifrost.animations.LedAnimationType

sealed class Terminator {
    data class Duration(val millis: Long) : Terminator()
    object UntilNextCommand : Terminator()
    object UntilExplicitClear : Terminator()
}

sealed class ExternalApiCommand {
    abstract val callerPackage: String
    abstract val requestId: String?
    /** Protocol version the caller declared (1..ExternalApi.API_VERSION). */
    open val apiVersion: Int get() = ExternalApi.MIN_API_VERSION

    data class Display(
        override val callerPackage: String,
        override val requestId: String?,
        val effect: LedAnimationType,
        val color: Int,
        val colorRight: Int,
        val intensity: Int,
        val speed: Float,
        val smoothness: Float,
        val sensitivity: Float,
        val terminator: Terminator,
        val priority: Int,
        val phaseSeconds: Float = 0f,
        val flickering: Boolean = false,
        val burstWallMs: Long = 0L,
        override val apiVersion: Int = ExternalApi.MIN_API_VERSION,
    ) : ExternalApiCommand()

    data class Clear(
        override val callerPackage: String,
        override val requestId: String?,
        override val apiVersion: Int = ExternalApi.MIN_API_VERSION,
    ) : ExternalApiCommand()

    /** v2: run a saved preset (by exact name) as this caller's override. */
    data class ApplyPreset(
        override val callerPackage: String,
        override val requestId: String?,
        val presetName: String,
        val terminator: Terminator,
        val priority: Int,
        /** Optional brightness override 0..255; null = the preset's own. */
        val intensity: Int?,
        override val apiVersion: Int,
    ) : ExternalApiCommand()

    /** v2: one ExternalFunction on the caller's own override. */
    data class Invoke(
        override val callerPackage: String,
        override val requestId: String?,
        val function: ExternalFunction,
        val intensity: Int,
        val pulseKind: String,
        val terminator: Terminator,
        val priority: Int,
        override val apiVersion: Int,
    ) : ExternalApiCommand()

    data class InstallProfile(
        override val callerPackage: String,
        override val requestId: String?,
        val profileName: String,
        val effect: LedAnimationType,
        val color: Int,
        val colorRight: Int,
        val intensity: Int,
        val speed: Float,
        val smoothness: Float,
        val sensitivity: Float,
        val saturationBoost: Float,
        val useCustomSampling: Boolean,
        val useSingleColor: Boolean,
        val breatheWhenCharging: Boolean,
        val indicateChargingSpeed: Boolean,
        val flashWhenReady: Boolean,
        val batteryLowColor: Int?,
        val batteryMidColor: Int?,
        val batteryHighColor: Int?,
        val cpuCoolColor: Int?,
        val cpuWarmColor: Int?,
        val cpuHotColor: Int?,
        val replaceIfExists: Boolean
    ) : ExternalApiCommand()

    data class UninstallProfile(
        override val callerPackage: String,
        override val requestId: String?,
        val profileName: String
    ) : ExternalApiCommand()
}

enum class ExternalFunction(val wireName: String) {
    BRIGHTNESS(ExternalApi.FUNCTION_BRIGHTNESS),
    PULSE(ExternalApi.FUNCTION_PULSE),
    PAUSE(ExternalApi.FUNCTION_PAUSE),
    RESUME(ExternalApi.FUNCTION_RESUME);

    companion object {
        fun fromWire(value: String?): ExternalFunction? =
            values().firstOrNull { it.wireName == value }
    }
}
