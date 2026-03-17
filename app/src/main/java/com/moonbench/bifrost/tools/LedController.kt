package com.moonbench.bifrost.tools

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LedController {
    companion object {
        private const val TAG = "LedController"
    }

    private val pServerBinder: IBinder?
    private val lock = ReentrantLock()

    private var lastCommand: String? = null
    private var lastExecuteTime = 0L
    private val minExecuteInterval = 16L

    init {
        pServerBinder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get PServerBinder", e)
            null
        }
    }

    fun setLedColor(
        red: Int,
        green: Int,
        blue: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        val br = brightness.coerceIn(0, 255)
        if (pServerBinder == null) return

        val commandBuilder = StringBuilder(220)

        if (leftTop) {
            commandBuilder.append("echo 1-").append(r).append(':').append(g).append(':').append(b).append(':').append(br)
                .append(" > /sys/class/sn3112l/led/brightness")
        }
        if (leftBottom) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 2-").append(r).append(':').append(g).append(':').append(b).append(':').append(br)
                .append(" > /sys/class/sn3112l/led/brightness")
        }
        if (rightTop) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 1-").append(r).append(':').append(g).append(':').append(b).append(':').append(br)
                .append(" > /sys/class/sn3112r/led/brightness")
        }
        if (rightBottom) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 2-").append(r).append(':').append(g).append(':').append(b).append(':').append(br)
                .append(" > /sys/class/sn3112r/led/brightness")
        }

        if (commandBuilder.isNotEmpty()) {
            executeCommandDirect(commandBuilder.toString())
        }
    }

    fun setBrightness(brightness: Int) {
        val b = brightness.coerceIn(0, 255)
        val sb = StringBuilder(220)
        sb.append("echo 1-0:0:0:").append(b).append(" > /sys/class/sn3112l/led/brightness")
          .append(" && echo 2-0:0:0:").append(b).append(" > /sys/class/sn3112l/led/brightness")
          .append(" && echo 1-0:0:0:").append(b).append(" > /sys/class/sn3112r/led/brightness")
          .append(" && echo 2-0:0:0:").append(b).append(" > /sys/class/sn3112r/led/brightness")
        executeCommandDirect(sb.toString())
    }

    private fun executeCommandDirect(command: String) {
        lock.withLock {
            val now = System.currentTimeMillis()

            if (command == lastCommand && now - lastExecuteTime < minExecuteInterval) {
                return
            }

            lastCommand = command
            lastExecuteTime = now

            pServerBinder?.let { binder ->
                val data = Parcel.obtain()
                // FLAG_ONEWAY: the binder never writes a reply, so pass null to avoid a
                // pointless Parcel allocation on every command.
                try {
                    data.writeStringArray(arrayOf(command, "1"))
                    binder.transact(0, data, null, IBinder.FLAG_ONEWAY)
                } catch (e: Exception) {
                    Log.w(TAG, "LED transact failed", e)
                } finally {
                    data.recycle()
                }
            }
        }
    }

    fun shutdown() {
    }
}