package com.moonbench.bifrost.services

import android.content.Intent
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class ServiceController(
    private val activity: AppCompatActivity,
    private val handler: Handler,
    private val debounceDelay: Long,
    private val restartDelay: Long
) {

    var isServiceTransitioning: Boolean = false
        private set

    private var isOperationInProgress = false
    private var lastOperationTime = 0L
    private var pendingServiceOperation: Runnable? = null

    var onNeedsMediaProjectionCheck: (() -> Unit)? = null

    fun cancelPendingOperations() {
        pendingServiceOperation?.let { handler.removeCallbacks(it) }
        pendingServiceOperation = null
    }

    fun startDebounced(createIntent: () -> Intent) {
        if (isOperationInProgress) return

        val now = System.currentTimeMillis()
        if (now - lastOperationTime < debounceDelay) {
            cancelPendingOperations()
        }

        lastOperationTime = now
        isOperationInProgress = true
        isServiceTransitioning = true

        pendingServiceOperation = Runnable {
            try {
                activity.startService(createIntent())
            } finally {
                isOperationInProgress = false
                handler.postDelayed({
                    isServiceTransitioning = false
                }, 200)
            }
        }

        handler.postDelayed(pendingServiceOperation!!, 100)
    }

    fun stopDebounced() {
        if (isOperationInProgress) return

        val now = System.currentTimeMillis()
        if (now - lastOperationTime < debounceDelay) {
            cancelPendingOperations()
        }

        lastOperationTime = now
        isOperationInProgress = true
        isServiceTransitioning = true

        pendingServiceOperation = Runnable {
            try {
                activity.stopService(Intent(activity, LEDService::class.java))
            } finally {
                isOperationInProgress = false
                handler.postDelayed({
                    isServiceTransitioning = false
                }, 200)
            }
        }

        handler.postDelayed(pendingServiceOperation!!, 100)
    }

    fun restartDebounced(needsMediaProjectionCheck: Boolean = false, createIntent: () -> Intent) {
        if (isOperationInProgress) return

        if (needsMediaProjectionCheck) {
            onNeedsMediaProjectionCheck?.invoke()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastOperationTime < debounceDelay) {
            cancelPendingOperations()
        }

        lastOperationTime = now
        isOperationInProgress = true
        isServiceTransitioning = true

        pendingServiceOperation = Runnable {
            try {
                activity.stopService(Intent(activity, LEDService::class.java))
                handler.postDelayed({
                    activity.startService(createIntent())
                    isOperationInProgress = false
                    handler.postDelayed({
                        isServiceTransitioning = false
                    }, 200)
                }, restartDelay)
            } catch (e: Exception) {
                isOperationInProgress = false
                isServiceTransitioning = false
            }
        }

        handler.postDelayed(pendingServiceOperation!!, 100)
    }
}