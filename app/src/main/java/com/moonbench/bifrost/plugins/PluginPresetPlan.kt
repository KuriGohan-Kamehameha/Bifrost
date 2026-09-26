package com.moonbench.bifrost.plugins

/** Validates names before an update can remove or replace installed presets. */
object PluginPresetPlan {
    fun validate(incoming: List<String>, reserved: Set<String>): String? {
        if (incoming.any { it.isBlank() }) return "Bundle contains an empty preset name"
        if (incoming.distinct().size != incoming.size) return "Bundle contains duplicate preset names"
        val collision = incoming.firstOrNull { it in reserved }
        return collision?.let { "Preset '$it' already belongs to you or another plugin" }
    }

    fun retiredNames(previous: List<String>, incoming: List<String>): List<String> {
        val retained = incoming.toSet()
        return previous.filterNot { it in retained }
    }
}
