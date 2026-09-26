package com.moonbench.bifrost.plugins

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PluginCatalogSafetyTest {
    private fun entry(id: String = "safe", url: String = "https://example.com/p.zip") =
        JSONObject().put("id", id).put("version", 1).put("bundleUrl", url)

    @Test fun rejectsCachePathTraversal() {
        for (id in listOf("../../settings", "/tmp/x", "a/b", "a\\b", ".", "..")) {
            assertNull("unsafe plugin id: $id", CatalogEntry.parseOrNull(entry(id)))
        }
    }
    @Test fun rejectsNonHttpsBundles() {
        for (url in listOf("http://example.com/p.zip", "file:///tmp/p.zip", "https:///missing-host")) {
            assertNull(url, CatalogEntry.parseOrNull(entry(url = url)))
        }
    }
    @Test fun rejectsMalformedExplicitChecksums() {
        assertNull(CatalogEntry.parseOrNull(entry().put("bundleSha256", "not-a-sha256")))
    }
    @Test fun rejectsInvalidVersions() {
        assertNull(CatalogEntry.parseOrNull(entry().put("version", -1)))
        assertNull(CatalogEntry.parseOrNull(entry().put("version", "bad")))
    }
    @Test fun rejectsMissingSchemaVersion() {
        assertThrows(CatalogParseException::class.java) {
            PluginCatalog.parse("""{"schema":"bifrost_plugin_catalog","plugins":[]}""")
        }
    }
    @Test fun rejectsDuplicatePluginIds() {
        val item = entry().toString()
        assertThrows(CatalogParseException::class.java) {
            PluginCatalog.parse("""{"schema":"bifrost_plugin_catalog","version":1,"plugins":[$item,$item]}""")
        }
    }
}
