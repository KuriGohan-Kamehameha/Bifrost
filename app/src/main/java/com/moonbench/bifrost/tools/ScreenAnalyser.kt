package com.moonbench.bifrost.tools

import android.graphics.Color
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import java.nio.ByteBuffer

data class ScreenColors(

    val leftColor: Int = Color.BLACK,

    val rightColor: Int = Color.BLACK,

    val topLeftColor: Int = Color.BLACK,

    val topRightColor: Int = Color.BLACK,

    val bottomLeftColor: Int = Color.BLACK,

    val bottomRightColor: Int = Color.BLACK

)

class ScreenAnalyzer(
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val regionType: ScreenRegionType,
    var performanceProfile: PerformanceProfile = PerformanceProfile.HIGH,
    private val onColorsAnalyzed: (ScreenColors) -> Unit
) {
    private var captureWidth = 2
    private var captureHeight = 2
    private var lastProcessedTime = 0L
    private var lastEmittedColors: ScreenColors? = null

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var projectionCallback: MediaProjection.Callback? = null

    fun start() {
        if (regionType == ScreenRegionType.TWO_SIDES) {
            captureWidth = 2
            captureHeight = 1
        } else {
            captureWidth = 2
            captureHeight = 2
        }

        handlerThread = HandlerThread("ScreenCapture").apply { start() }
        handler = Handler(handlerThread!!.looper)

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }
        mediaProjection.registerCallback(projectionCallback!!, handler)

        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            android.graphics.PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            val image = reader.acquireLatestImage()

            if (image != null) {
                if (performanceProfile == PerformanceProfile.RAGNAROK || now - lastProcessedTime >= performanceProfile.intervalMs) {
                    processImage(image)
                    lastProcessedTime = now
                }
                image.close()
            }
        }, handler)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            captureWidth,
            captureHeight,
            displayMetrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    fun stop() {
        virtualDisplay?.release()
        imageReader?.close()
        handlerThread?.quitSafely()

        projectionCallback?.let {
            mediaProjection.unregisterCallback(it)
        }

        virtualDisplay = null
        imageReader = null
        handlerThread = null
        handler = null
        projectionCallback = null
        lastEmittedColors = null
    }

    private fun processImage(image: Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val colors = if (regionType == ScreenRegionType.TWO_SIDES) {
            val leftColor = getPixelColor(buffer, 0, 0, rowStride, pixelStride)
            val rightColor = getPixelColor(buffer, 1, 0, rowStride, pixelStride)
            ScreenColors(leftColor = leftColor, rightColor = rightColor)
        } else {
            val topLeft = getPixelColor(buffer, 0, 0, rowStride, pixelStride)
            val topRight = getPixelColor(buffer, 1, 0, rowStride, pixelStride)
            val bottomLeft = getPixelColor(buffer, 0, 1, rowStride, pixelStride)
            val bottomRight = getPixelColor(buffer, 1, 1, rowStride, pixelStride)

            ScreenColors(
                topLeftColor = topLeft,
                topRightColor = topRight,
                bottomLeftColor = bottomLeft,
                bottomRightColor = bottomRight
            )
        }

        if (colors != lastEmittedColors) {
            lastEmittedColors = colors
            onColorsAnalyzed(colors)
        }
    }

    private fun getPixelColor(buffer: ByteBuffer, x: Int, y: Int, rowStride: Int, pixelStride: Int): Int {
        val offset = y * rowStride + x * pixelStride

        val r = buffer.get(offset).toInt() and 0xFF
        val g = buffer.get(offset + 1).toInt() and 0xFF
        val b = buffer.get(offset + 2).toInt() and 0xFF
        val a = buffer.get(offset + 3).toInt() and 0xFF

        return Color.argb(a, r, g, b)
    }
}