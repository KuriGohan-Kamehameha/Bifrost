package com.moonbench.bifrost.capture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.view.Surface

/** Service-owned projection and display; animations only attach/detach surfaces. */
class ProjectionCapture(
    val projection: MediaProjection,
    handler: Handler,
    private val onRevoked: (ProjectionCapture) -> Unit
) {
    val display = CaptureDisplay<Surface, VirtualDisplay>(
        create = { surface, width, height, density ->
            projection.createVirtualDisplay("BifrostCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, handler)
                ?: error("Android did not create a capture display")
        },
        update = { existing, surface, width, height, density ->
            existing.resize(width, height, density)
            existing.surface = surface
        },
        detach = { it.surface = null },
        release = { it.release() }
    )
    private var closed = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            try { close() }
            catch (e: Exception) { android.util.Log.w("ProjectionCapture", "Capture cleanup failed", e) }
            finally { onRevoked(this@ProjectionCapture) }
        }
    }

    init { projection.registerCallback(callback, handler) }

    @Synchronized fun close() {
        if (closed) return
        closed = true
        try {
            display.close()
        } finally {
            try { projection.unregisterCallback(callback) }
            finally { projection.stop() }
        }
    }
}
