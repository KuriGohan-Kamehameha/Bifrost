package com.moonbench.bifrost.tools

import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LedController {
    private val pServerBinder: IBinder?
    private val lock = ReentrantLock()

    private var lastCommand: String? = null
    private var lastExecuteTime = 0L
    private val minExecuteInterval = 8L

    init {
        pServerBinder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        } catch (e: Exception) {
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
        val commands = mutableListOf<String>()

        if (leftTop) {
            commands.add("echo 1-$red:$green:$blue:$brightness > /sys/class/sn3112l/led/brightness")
        }
        if (leftBottom) {
            commands.add("echo 2-$red:$green:$blue:$brightness > /sys/class/sn3112l/led/brightness")
        }
        if (rightTop) {
            commands.add("echo 1-$red:$green:$blue:$brightness > /sys/class/sn3112r/led/brightness")
        }
        if (rightBottom) {
            commands.add("echo 2-$red:$green:$blue:$brightness > /sys/class/sn3112r/led/brightness")
        }

        if (commands.isNotEmpty()) {
            val command = commands.joinToString(" && ")
            executeCommandDirect(command)
        }
    }

    fun setBrightness(brightness: Int) {
        val b = brightness.coerceIn(0, 255)
        val commands = listOf(
            "echo 1-0:0:0:$b > /sys/class/sn3112l/led/brightness",
            "echo 2-0:0:0:$b > /sys/class/sn3112l/led/brightness",
            "echo 1-0:0:0:$b > /sys/class/sn3112r/led/brightness",
            "echo 2-0:0:0:$b > /sys/class/sn3112r/led/brightness"
        )
        val command = commands.joinToString(" && ")
        executeCommandDirect(command)
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
                val reply = Parcel.obtain()

                try {
                    data.writeStringArray(arrayOf(command, "1"))
                    binder.transact(0, data, reply, IBinder.FLAG_ONEWAY)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }

    fun shutdown() {
    }
}