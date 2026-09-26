package com.moonbench.bifrost.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.Q)
class AudioAnalyzer(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val performanceProfile: PerformanceProfile,
    private val callback: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioAnalyzer"
        private const val SAMPLE_RATE_HZ = 8000
        private const val DEFAULT_BUFFER_BYTES = 512
        private const val THREAD_JOIN_TIMEOUT_MS = 200L
    }

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var routingListener: AudioRouting.OnRoutingChangedListener? = null

    @Volatile
    private var running = false

    private var sampleBuffer = ShortArray(256)

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (running || captureThread?.isAlive == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Playback capture needs audio permission")
            return
        }

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val sampleRate = SAMPLE_RATE_HZ
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            check(minBufferSize > 0) { "Unsupported audio buffer configuration: $minBufferSize" }
            val bufferSize = maxOf(DEFAULT_BUFFER_BYTES, minBufferSize)
            sampleBuffer = ShortArray((bufferSize / 2).coerceAtLeast(128))

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            val record = audioRecord
            if (record?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord failed to initialize")
                cleanup()
                return
            }

            routingListener = AudioRouting.OnRoutingChangedListener { route ->
                val audioRoute = route as? AudioRecord ?: return@OnRoutingChangedListener
                if (HardwareDeviceBlacklist.isBlockedMicrophoneDevice(audioRoute.routedDevice)) {
                    Log.w(TAG, "Blocked physical microphone route detected; stopping capture")
                    running = false
                }
            }
            routingListener?.let { listener ->
                record.addOnRoutingChangedListener(listener, null)
            }

            running = true

            captureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                try {
                    record.startRecording()

                    if (HardwareDeviceBlacklist.isBlockedMicrophoneDevice(record.routedDevice)) {
                        Log.w(TAG, "Initial route resolved to blocked microphone; aborting capture")
                        running = false
                        return@Thread
                    }

                    var skip = 0
                    val skipInterval = when {
                        performanceProfile.intervalMs >= 32L -> 3
                        performanceProfile.intervalMs >= 16L -> 1
                        else -> 0
                    }

                    while (running) {
                        val read = record.read(sampleBuffer, 0, sampleBuffer.size, AudioRecord.READ_NON_BLOCKING)
                        if (read < 0) {
                            Log.w(TAG, "Audio capture stopped with code $read")
                            break
                        }
                        if (read == 0) {
                            Thread.sleep(8)
                            continue
                        }

                        if (read > 0) {
                            if (skip > 0) {
                                skip--
                                continue
                            }
                            skip = skipInterval

                            var max = 0
                            var i = 0
                            val limit = minOf(read, sampleBuffer.size)
                            while (i < limit) {
                                val abs = abs(sampleBuffer[i].toInt())
                                if (abs > max) max = abs
                                i++
                            }

                            val intensity = (max.toFloat() / Short.MAX_VALUE * 5f).coerceIn(0f, 1f)
                            if (running) callback(intensity)
                        }
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    Log.w(TAG, "Audio capture loop failed", e)
                } finally {
                    running = false
                    cleanup()
                }
            }, "AudioCapture")

            captureThread?.start()

        } catch (e: SecurityException) {
            Log.w(TAG, "Audio permission or capture consent was revoked", e)
            running = false
            cleanup()
        } catch (e: Exception) {
            Log.w(TAG, "Audio analyzer failed to start", e)
            running = false
            cleanup()
        }
    }

    fun stop() {
        running = false
        val thread = captureThread
        thread?.interrupt()
        if (thread != null && thread !== Thread.currentThread()) {
            try { thread.join(THREAD_JOIN_TIMEOUT_MS) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        if (thread?.isAlive == true) {
            // The worker owns the record until it exits. Do not release under read().
            Log.w(TAG, "Audio worker is finishing shutdown")
            return
        }
        captureThread = null
        cleanup()
    }

    @Synchronized private fun cleanup() {
        try {
            audioRecord?.let { record ->
                routingListener?.let { listener ->
                    runCatching { record.removeOnRoutingChangedListener(listener) }
                }
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio record cleanup failed", e)
        }

        audioRecord = null
        routingListener = null
    }
}