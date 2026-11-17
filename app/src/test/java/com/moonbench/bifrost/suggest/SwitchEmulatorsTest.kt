package com.moonbench.bifrost.suggest

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SwitchEmulatorsTest {
    private val ns = "http://schemas.android.com/apk/res/android"

    @Test fun tableCoversEdenNightlyAndDuo() {
        val ids = SwitchEmulators.KNOWN.map { it.packageName }
        assertTrue("dev.eden.eden_emulator" in ids)
        assertTrue("dev.eden.eden_emulator.nightly" in ids)
        assertTrue("dev.igawa6.edenduo" in ids)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(SwitchEmulators.KNOWN.all { it.displayName.isNotBlank() })
        assertEquals("Eden Duo", SwitchEmulators.displayName("dev.igawa6.edenduo"))
        assertEquals("com.unknown", SwitchEmulators.displayName("com.unknown"))
    }

    @Test fun detectKeepsTableOrderAndOnlyInstalled() {
        val installed = setOf("dev.igawa6.edenduo", "dev.eden.eden_emulator.nightly", "org.other")
        assertEquals(
            listOf("dev.eden.eden_emulator.nightly", "dev.igawa6.edenduo"),
            SwitchEmulators.detect { it in installed }.map { it.packageName })
        assertTrue(SwitchEmulators.detect { false }.isEmpty())
    }

    @Test fun manifestQueriesEveryDetectedPackage() {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest)
        val queries = doc.getElementsByTagName("queries")
        assertEquals(1, queries.length)
        val pkgs = (queries.item(0) as org.w3c.dom.Element).getElementsByTagName("package")
        val declared = (0 until pkgs.length).map {
            (pkgs.item(it) as org.w3c.dom.Element).getAttributeNS(ns, "name")
        }.toSet()
        SwitchEmulators.KNOWN.forEach { assertTrue(it.packageName, it.packageName in declared) }
        assertTrue(CocoonTheme.PACKAGE in declared)
        val perms = doc.getElementsByTagName("uses-permission")
        assertFalse((0 until perms.length).any {
            (perms.item(it) as org.w3c.dom.Element).getAttributeNS(ns, "name") ==
                "android.permission.QUERY_ALL_PACKAGES"
        })
    }
}
