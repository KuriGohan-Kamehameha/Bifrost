package com.moonbench.bifrost

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class ManifestPermissionsTest {
    @Test fun playbackCaptureDeclaresPermissionWithoutRequiringMicrophoneHardware() {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(manifest)
        val ns = "http://schemas.android.com/apk/res/android"
        val permissions = doc.getElementsByTagName("uses-permission")
        assertTrue((0 until permissions.length).any {
            (permissions.item(it) as org.w3c.dom.Element).getAttributeNS(ns, "name") == "android.permission.RECORD_AUDIO"
        })
        val features = doc.getElementsByTagName("uses-feature")
        assertTrue((0 until features.length).any {
            val element = features.item(it) as org.w3c.dom.Element
            element.getAttributeNS(ns, "name") == "android.hardware.microphone" &&
                element.getAttributeNS(ns, "required") == "false"
        })
    }
}
