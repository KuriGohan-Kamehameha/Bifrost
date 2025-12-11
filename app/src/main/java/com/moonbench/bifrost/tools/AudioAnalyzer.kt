package com.moonbench.bifrost.tools

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import kotlin.math.sqrt

@RequiresApi(Build.VERSION_CODES.Q)
class AudioAnalyzer(
    private val mediaProjection: MediaProjection,
    private val performanceProfile: PerformanceProfile,
    private val callback: (Float) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    @Volatile
    private var running = false

    private val frameInterval: Long
        get() = if (performanceProfile.intervalMs == 0L) 16L else performanceProfile.intervalMs

    private val analysisRunnable = object : Runnable {
        private val buffer = ShortArray(512)

        override fun run() {
            if (!running) return

            val record = audioRecord
            if (record == null || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                if (running) {
                    handler?.postDelayed(this, frameInterval)
                }
                return
            }

            try {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)

                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        val v = buffer[i].toDouble() / Short.MAX_VALUE
                        sum += v * v
                    }
                    val rms = sqrt(sum / read)
                    val intensity = (rms * 5f).toFloat().coerceIn(0f, 1f)
                    callback(intensity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (running) {
                handler?.postDelayed(this, frameInterval)
            }
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (running) return

        try {
            handlerThread = HandlerThread("AudioAnalyzer").apply { start() }
            handler = Handler(handlerThread!!.looper)

            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val sampleRate = 22050
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            val recordBufferSize = if (minBuffer > 0) minBuffer else sampleRate / 2

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(recordBufferSize)
                .build()

            val record = audioRecord
            if (record?.state != AudioRecord.STATE_INITIALIZED) {
                cleanup()
                return
            }

            running = true

            handler?.postDelayed({
                try {
                    record.startRecording()
                    handler?.post(analysisRunnable)
                } catch (e: Exception) {
                    e.printStackTrace()
                    running = false
                    cleanup()
                }
            }, 150)

        } catch (e: Exception) {
            e.printStackTrace()
            running = false
            cleanup()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        handler?.removeCallbacks(analysisRunnable)

        val localHandler = handler
        if (localHandler != null) {
            localHandler.post {
                cleanup()
            }
        } else {
            cleanup()
        }
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

        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }
}
