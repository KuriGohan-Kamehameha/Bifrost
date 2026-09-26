package com.moonbench.bifrost.plugins

import org.junit.Assert.*
import org.junit.Test

class PluginPresetPlanTest {
    @Test fun userPresetCannotBeShadowedByPlugin() {
        assertNotNull(PluginPresetPlan.validate(listOf("Reading"), setOf("Reading")))
    }

    @Test fun duplicateAndEmptyNamesAreRejected() {
        assertNotNull(PluginPresetPlan.validate(listOf("Game", "Game"), emptySet()))
        assertNotNull(PluginPresetPlan.validate(listOf("  "), emptySet()))
    }

    @Test fun pluginCanReplaceItsOwnNames() {
        assertNull(PluginPresetPlan.validate(listOf("Game"), setOf("Reading")))
    }

    @Test fun updatePreservesMappingsToRetainedPresets() {
        assertEquals(listOf("Old"), PluginPresetPlan.retiredNames(
            listOf("Game", "Old"), listOf("Game", "New")))
        assertEquals(emptyList<String>(), PluginPresetPlan.retiredNames(listOf("Game"), listOf("Game")))
    }
}
