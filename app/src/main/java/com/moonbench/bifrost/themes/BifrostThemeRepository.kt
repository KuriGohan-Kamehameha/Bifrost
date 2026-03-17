package com.moonbench.bifrost

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HeaderThemePalette(
    val introHueStart: Float,
    val introHueSpan: Float,
    val introSaturation: Float,
    val introValue: Float,
    val settleHueStart: Float,
    val settleHueSpan: Float,
    val settleHueWobbleAmplitude: Float,
    val settleSaturationBase: Float,
    val settleSaturationWave: Float,
    val settleValueBase: Float,
    val settleValueWave: Float,
    val settleAlphaBase: Int,
    val settleAlphaWave: Int,
    val settlePhaseDegrees: Float
)

data class BifrostUiTheme(
    val id: String,
    val name: String,
    val backgroundColor: Int,
    val cardColor: Int,
    val surfaceColor: Int,
    val textColor: Int,
    val textSecondaryColor: Int,
    val accentColor: Int,
    val accentLightColor: Int,
    val headerPalette: HeaderThemePalette,
    val builtIn: Boolean
)

object BifrostThemeCatalog {
    private val classicHeader = HeaderThemePalette(
        introHueStart = 0f,
        introHueSpan = 360f,
        introSaturation = 0.82f,
        introValue = 1f,
        settleHueStart = 18f,
        settleHueSpan = 300f,
        settleHueWobbleAmplitude = 10f,
        settleSaturationBase = 0.28f,
        settleSaturationWave = 0.14f,
        settleValueBase = 0.92f,
        settleValueWave = 0.08f,
        settleAlphaBase = 224,
        settleAlphaWave = 31,
        settlePhaseDegrees = 18f
    )

    private val oledPurpleHeader = HeaderThemePalette(
        introHueStart = 264f,
        introHueSpan = 64f,
        introSaturation = 0.9f,
        introValue = 0.98f,
        settleHueStart = 272f,
        settleHueSpan = 48f,
        settleHueWobbleAmplitude = 5f,
        settleSaturationBase = 0.5f,
        settleSaturationWave = 0.16f,
        settleValueBase = 0.76f,
        settleValueWave = 0.2f,
        settleAlphaBase = 236,
        settleAlphaWave = 19,
        settlePhaseDegrees = 12f
    )

    val classicTheme = BifrostUiTheme(
        id = "classic",
        name = "Classic",
        backgroundColor = Color.parseColor("#0A0E1A"),
        cardColor = Color.parseColor("#12182B"),
        surfaceColor = Color.parseColor("#1A2236"),
        textColor = Color.parseColor("#E8EEFF"),
        textSecondaryColor = Color.parseColor("#7A8AA8"),
        accentColor = Color.parseColor("#6366F1"),
        accentLightColor = Color.parseColor("#818CF8"),
        headerPalette = classicHeader,
        builtIn = true
    )

    val oledPurpleTheme = BifrostUiTheme(
        id = "oled_purple",
        name = "OLED Purple",
        backgroundColor = Color.parseColor("#000000"),
        cardColor = Color.parseColor("#000000"),
        surfaceColor = Color.parseColor("#000000"),
        textColor = Color.parseColor("#C084FC"),
        textSecondaryColor = Color.parseColor("#A855F7"),
        accentColor = Color.parseColor("#A855F7"),
        accentLightColor = Color.parseColor("#C084FC"),
        headerPalette = oledPurpleHeader,
        builtIn = true
    )

    val builtInThemes: List<BifrostUiTheme> = listOf(classicTheme, oledPurpleTheme)

    fun defaultTheme(): BifrostUiTheme = classicTheme
}

class BifrostThemeRepository(private val prefs: SharedPreferences) {

    companion object {
        private const val PREF_CUSTOM_THEMES_JSON = "custom_themes_json"
        private const val PREF_SELECTED_THEME_ID = "selected_theme_id"
        private const val THEME_FILE_SCHEMA = "bifrost_theme"
        private const val THEME_FILE_VERSION = 1
    }

    fun availableThemes(): List<BifrostUiTheme> {
        return BifrostThemeCatalog.builtInThemes + loadCustomThemes()
    }

    fun selectedTheme(): BifrostUiTheme {
        val selectedId = prefs.getString(PREF_SELECTED_THEME_ID, null)
        return availableThemes().firstOrNull { it.id == selectedId } ?: BifrostThemeCatalog.defaultTheme()
    }

    fun setSelectedThemeId(themeId: String) {
        prefs.edit().putString(PREF_SELECTED_THEME_ID, themeId).apply()
    }

    fun saveImportedTheme(imported: BifrostUiTheme): BifrostUiTheme {
        val builtInIds = BifrostThemeCatalog.builtInThemes.map { it.id }.toSet()
        val customThemes = loadCustomThemes().toMutableList()

        val normalizedId = when {
            imported.id.isBlank() -> "imported_${UUID.randomUUID()}"
            imported.id in builtInIds -> "imported_${imported.id}_${UUID.randomUUID()}"
            else -> imported.id
        }

        val normalized = imported.copy(id = normalizedId, builtIn = false)

        val replaceIndex = customThemes.indexOfFirst { it.id == normalized.id }
        if (replaceIndex >= 0) {
            customThemes[replaceIndex] = normalized
        } else {
            customThemes += normalized
        }

        saveCustomThemes(customThemes)
        return normalized
    }

    fun exportThemeToUri(context: Context, theme: BifrostUiTheme, uri: Uri) {
        val root = JSONObject().apply {
            put("schema", THEME_FILE_SCHEMA)
            put("version", THEME_FILE_VERSION)
            put("theme", themeToJson(theme))
        }

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open export destination")
    }

    fun importThemeFromUri(context: Context, uri: Uri): BifrostUiTheme {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("Unable to read theme file")

        val root = JSONObject(raw)
        val schema = root.optString("schema", THEME_FILE_SCHEMA)
        if (schema != THEME_FILE_SCHEMA) {
            error("Unsupported theme schema")
        }

        val version = root.optInt("version", -1)
        if (version <= 0) {
            error("Invalid theme version")
        }

        val themeJson = root.optJSONObject("theme") ?: root
        return jsonToTheme(themeJson, builtIn = false)
    }

    private fun loadCustomThemes(): List<BifrostUiTheme> {
        val json = prefs.getString(PREF_CUSTOM_THEMES_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(jsonToTheme(item, builtIn = false))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomThemes(themes: List<BifrostUiTheme>) {
        val array = JSONArray()
        themes.forEach { theme ->
            array.put(themeToJson(theme))
        }
        prefs.edit().putString(PREF_CUSTOM_THEMES_JSON, array.toString()).apply()
    }

    private fun themeToJson(theme: BifrostUiTheme): JSONObject {
        val palette = theme.headerPalette
        return JSONObject().apply {
            put("id", theme.id)
            put("name", theme.name)
            put("backgroundColor", theme.backgroundColor)
            put("cardColor", theme.cardColor)
            put("surfaceColor", theme.surfaceColor)
            put("textColor", theme.textColor)
            put("textSecondaryColor", theme.textSecondaryColor)
            put("accentColor", theme.accentColor)
            put("accentLightColor", theme.accentLightColor)
            put("headerPalette", JSONObject().apply {
                put("introHueStart", palette.introHueStart)
                put("introHueSpan", palette.introHueSpan)
                put("introSaturation", palette.introSaturation)
                put("introValue", palette.introValue)
                put("settleHueStart", palette.settleHueStart)
                put("settleHueSpan", palette.settleHueSpan)
                put("settleHueWobbleAmplitude", palette.settleHueWobbleAmplitude)
                put("settleSaturationBase", palette.settleSaturationBase)
                put("settleSaturationWave", palette.settleSaturationWave)
                put("settleValueBase", palette.settleValueBase)
                put("settleValueWave", palette.settleValueWave)
                put("settleAlphaBase", palette.settleAlphaBase)
                put("settleAlphaWave", palette.settleAlphaWave)
                put("settlePhaseDegrees", palette.settlePhaseDegrees)
            })
        }
    }

    private fun jsonToTheme(json: JSONObject, builtIn: Boolean): BifrostUiTheme {
        val paletteJson = json.optJSONObject("headerPalette")
        val fallback = BifrostThemeCatalog.defaultTheme().headerPalette

        val headerPalette = HeaderThemePalette(
            introHueStart = paletteJson?.optDouble("introHueStart", fallback.introHueStart.toDouble())?.toFloat()
                ?: fallback.introHueStart,
            introHueSpan = paletteJson?.optDouble("introHueSpan", fallback.introHueSpan.toDouble())?.toFloat()
                ?: fallback.introHueSpan,
            introSaturation = paletteJson?.optDouble("introSaturation", fallback.introSaturation.toDouble())?.toFloat()
                ?: fallback.introSaturation,
            introValue = paletteJson?.optDouble("introValue", fallback.introValue.toDouble())?.toFloat()
                ?: fallback.introValue,
            settleHueStart = paletteJson?.optDouble("settleHueStart", fallback.settleHueStart.toDouble())?.toFloat()
                ?: fallback.settleHueStart,
            settleHueSpan = paletteJson?.optDouble("settleHueSpan", fallback.settleHueSpan.toDouble())?.toFloat()
                ?: fallback.settleHueSpan,
            settleHueWobbleAmplitude = paletteJson?.optDouble("settleHueWobbleAmplitude", fallback.settleHueWobbleAmplitude.toDouble())?.toFloat()
                ?: fallback.settleHueWobbleAmplitude,
            settleSaturationBase = paletteJson?.optDouble("settleSaturationBase", fallback.settleSaturationBase.toDouble())?.toFloat()
                ?: fallback.settleSaturationBase,
            settleSaturationWave = paletteJson?.optDouble("settleSaturationWave", fallback.settleSaturationWave.toDouble())?.toFloat()
                ?: fallback.settleSaturationWave,
            settleValueBase = paletteJson?.optDouble("settleValueBase", fallback.settleValueBase.toDouble())?.toFloat()
                ?: fallback.settleValueBase,
            settleValueWave = paletteJson?.optDouble("settleValueWave", fallback.settleValueWave.toDouble())?.toFloat()
                ?: fallback.settleValueWave,
            settleAlphaBase = paletteJson?.optInt("settleAlphaBase", fallback.settleAlphaBase) ?: fallback.settleAlphaBase,
            settleAlphaWave = paletteJson?.optInt("settleAlphaWave", fallback.settleAlphaWave) ?: fallback.settleAlphaWave,
            settlePhaseDegrees = paletteJson?.optDouble("settlePhaseDegrees", fallback.settlePhaseDegrees.toDouble())?.toFloat()
                ?: fallback.settlePhaseDegrees
        )

        val fallbackTheme = BifrostThemeCatalog.defaultTheme()
        return BifrostUiTheme(
            id = json.optString("id").ifBlank { "imported_${UUID.randomUUID()}" },
            name = json.optString("name").ifBlank { "Imported Theme" },
            backgroundColor = json.optInt("backgroundColor", fallbackTheme.backgroundColor),
            cardColor = json.optInt("cardColor", fallbackTheme.cardColor),
            surfaceColor = json.optInt("surfaceColor", fallbackTheme.surfaceColor),
            textColor = json.optInt("textColor", fallbackTheme.textColor),
            textSecondaryColor = json.optInt("textSecondaryColor", fallbackTheme.textSecondaryColor),
            accentColor = json.optInt("accentColor", fallbackTheme.accentColor),
            accentLightColor = json.optInt("accentLightColor", fallbackTheme.accentLightColor),
            headerPalette = headerPalette,
            builtIn = builtIn
        )
    }
}
