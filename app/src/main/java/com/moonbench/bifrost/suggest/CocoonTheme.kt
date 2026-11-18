package com.moonbench.bifrost.suggest

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Match Cocoon": when the Cocoon launcher is installed, Bifrost's neutral
 * colours follow the device's theme.
 *
 * Theme source: Cocoon (rip.moth.cocoonshell 3.11) exposes no readable theme.
 * It has no exported ContentProvider (only androidx InitializationProvider and
 * a non-exported FileProvider), sends no theme broadcasts, and keeps the active
 * theme in its private storage; the theme packs under /sdcard/Cocoon/themes
 * are owned by Cocoon and unreadable without all-files access, and say nothing
 * about which pack is active. The closest readable match is Android's Material
 * You system palette (android.R.color.system_*), which follows the wallpaper
 * Cocoon displays. If that palette is missing or colourless, nothing changes.
 */
object CocoonTheme {

    const val PACKAGE = "rip.moth.cocoonshell"
    const val OWNER = "bifrost:cocoon-match"
    val NAME_CANDIDATES = listOf("Cocoon Match", "Cocoon Match (Bifrost)")
    const val EMOJI = "🧶" // yarn: Cocoon's silk
    const val UI_THEME_ID = "system_palette"

    /** The few system palette tones Bifrost uses. */
    data class Palette(
        val accent: Int,
        val accentLight: Int,
        val background: Int,
        val card: Int,
        val surface: Int,
        val text: Int
    )

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(PACKAGE, PackageManager.ApplicationInfoFlags.of(0)).enabled
    }.getOrDefault(false)

    /** Reads the Material You palette; null when the device does not provide one. */
    fun readSystemPalette(context: Context): Palette? = runCatching {
        Palette(
            accent = context.getColor(android.R.color.system_accent1_500),
            accentLight = context.getColor(android.R.color.system_accent1_200),
            background = context.getColor(android.R.color.system_neutral1_900),
            card = context.getColor(android.R.color.system_neutral1_800),
            surface = context.getColor(android.R.color.system_neutral2_800),
            text = context.getColor(android.R.color.system_neutral1_50)
        )
    }.getOrNull()?.takeIf { ledColor(it.accent) != null }

    /**
     * The accent pushed to full brightness and LED-strength saturation, so a
     * pastel UI accent still reads as the same hue on the stick LEDs. Null for
     * an (almost) grey accent: there is no hue to match.
     */
    fun ledColor(accent: Int): Int? {
        val r = (accent shr 16 and 0xFF) / 255f
        val g = (accent shr 8 and 0xFF) / 255f
        val b = (accent and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        if (maxC <= 0f || delta / maxC < MIN_SATURATION) return null
        val hue = when (maxC) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val sat = max(delta / maxC, LED_MIN_SATURATION)
        return hsvToColor(hue, sat, 1f)
    }

    data class Outcome(val presets: JSONArray, val name: String?, val changed: Boolean)

    /**
     * Creates or refreshes the owned neutral preset. It becomes the app-profile
     * fallback only when no other preset is the fallback, so a user's choice is
     * never replaced. Unlike the Switch preset its colours are re-synced each
     * time, because following the theme is its whole purpose.
     */
    fun upsert(presets: JSONArray, palette: Palette): Outcome {
        val led = ledColor(palette.accent) ?: return Outcome(OwnedPresets.copy(presets), null, false)
        val out = OwnedPresets.copy(presets)
        val owned = OwnedPresets.findOwned(out, OWNER)
        if (owned != null) {
            val changed = owned.optInt("color") != led || owned.optInt("rightColor") != led
            owned.put("color", led)
            owned.put("rightColor", led)
            return Outcome(out, owned.optString("name"), changed)
        }
        val ensured = OwnedPresets.ensure(out, OWNER, NAME_CANDIDATES) { name ->
            OwnedPresets.staticPreset(
                name, OWNER, led, led, EMOJI,
                isAppProfileDefault = !OwnedPresets.hasDefault(out)
            )
        }
        return Outcome(ensured.presets, ensured.name, true)
    }

    fun remove(presets: JSONArray, mappings: Map<String, String>): SwitchProfilePlan.Outcome {
        val removed = OwnedPresets.removeOwned(presets, OWNER)
        val kept = OwnedPresets.mappingsWithout(mappings, removed.removedNames)
        return SwitchProfilePlan.Outcome(
            removed.presets, kept, removed.removedNames.firstOrNull(),
            mappings.keys.filter { it !in kept })
    }

    private const val MIN_SATURATION = 0.08f
    private const val LED_MIN_SATURATION = 0.85f

    internal fun hsvToColor(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
