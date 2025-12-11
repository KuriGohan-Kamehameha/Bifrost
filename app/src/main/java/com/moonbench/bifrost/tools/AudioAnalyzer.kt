package com.moonbench.bifrost.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.Q)
class AudioAnalyzer(
    private val mediaProjection: MediaProjection,
    private val performanceProfile: PerformanceProfile,
    private val callback: (Float) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    @Volatile
    private var running = false

    private val buffer = ShortArray(32)

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (running) return

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = 512

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
                cleanup()
                return
            }

            running = true

            captureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                try {
                    record.startRecording()

                    var skip = 0
                    val skipInterval = when {
                        performanceProfile.intervalMs >= 32L -> 3
                        performanceProfile.intervalMs >= 16L -> 1
                        else -> 0
                    }

                    while (running) {
                        val read = record.read(buffer, 0, buffer.size)

                        if (read > 0) {
                            if (skip > 0) {
                                skip--
                                continue
                            }
                            skip = skipInterval

                            var max = 0
                            var i = 0
                            while (i < read) {
                                val abs = abs(buffer[i].toInt())
                                if (abs > max) max = abs
                                i++
                            }

                            val intensity = (max.toFloat() / Short.MAX_VALUE * 5f).coerceIn(0f, 1f)
                            callback(intensity)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, "AudioCapture")

            captureThread?.priority = Thread.MAX_PRIORITY
            captureThread?.start()

        } catch (e: Exception) {
            e.printStackTrace()
            running = false
            cleanup()
        }
    }

    fun stop() {
        if (!running) return
        running = false

        captureThread?.interrupt()
        captureThread?.join(100)
        captureThread = null

        cleanup()
    }

    private fun cleanup() {
        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audioRecord = null
    }
}