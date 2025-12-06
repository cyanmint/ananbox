package com.github.ananbox

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.os.Process
import android.view.View
import kotlin.system.exitProcess

object Anbox: View.OnTouchListener {
    init {
        System.loadLibrary("anbox")
    }
    
    // Track shutdown attempt count: 0=none, 1=SIGINT sent, 2=SIGTERM sent
    @Volatile
    private var shutdownAttemptCount = 0

    external fun stringFromJNI(): String
    external fun setPath(path: String)
    external fun startRuntime()
    external fun destroyWindow()
    external fun stopRuntime()
    external fun startContainer(proot: String)
    external fun resetWindow(height: Int, width: Int)
    external fun createSurface(surface: Surface)
    external fun destroySurface()
    // pipe including Renderer, GPS & Sensor, input manager
    external fun initRuntime(width: Int, height: Int, dpi: Int): Boolean
    external fun pushFingerUp(i: Int)
    external fun pushFingerDown(x: Int, y: Int, fingerId: Int)
    external fun pushFingerMotion(x: Int, y: Int, fingerId: Int)

    /**
     * Stop the container with escalating signals:
     * - First click: SIGINT (Ctrl+C) - allows graceful cleanup
     * - Second click: SIGTERM - stronger termination request
     * - Third click: SIGKILL - forceful immediate termination
     * Returns the signal that was sent: "SIGINT", "SIGTERM", or "SIGKILL"
     */
    fun stopContainer(): String {
        Log.d("Anbox", "stopContainer - shutdownAttemptCount=$shutdownAttemptCount")
        
        val signal = when (shutdownAttemptCount) {
            0 -> {
                // First click: send SIGINT (2) - like Ctrl+C
                shutdownAttemptCount = 1
                try {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "kill -INT \$(pidof init) 2>/dev/null || killall -INT init 2>/dev/null"))
                    Log.d("Anbox", "Sent SIGINT to init")
                } catch (e: Exception) {
                    Log.e("Anbox", "Failed to send SIGINT to init", e)
                }
                "SIGINT"
            }
            1 -> {
                // Second click: send SIGTERM (15)
                shutdownAttemptCount = 2
                try {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "kill -TERM \$(pidof init) 2>/dev/null || killall -TERM init 2>/dev/null"))
                    Log.d("Anbox", "Sent SIGTERM to init")
                } catch (e: Exception) {
                    Log.e("Anbox", "Failed to send SIGTERM to init", e)
                }
                "SIGTERM"
            }
            else -> {
                // Third+ click: send SIGKILL (9) - forceful
                shutdownAttemptCount = 0 // Reset for next time
                try {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "kill -KILL \$(pidof init) 2>/dev/null || killall -KILL init 2>/dev/null"))
                    Log.d("Anbox", "Sent SIGKILL to init")
                } catch (e: Exception) {
                    Log.e("Anbox", "Failed to send SIGKILL to init", e)
                }
                "SIGKILL"
            }
        }
        return signal
    }
    
    /**
     * Reset the shutdown state (e.g., when container is started again)
     */
    fun resetShutdownState() {
        shutdownAttemptCount = 0
    }

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                pushFingerDown(e.x.toInt(), e.y.toInt(), 0)
            }

            MotionEvent.ACTION_UP -> {
                pushFingerUp(0)
            }

            MotionEvent.ACTION_MOVE -> {
                pushFingerMotion(e.x.toInt(), e.y.toInt(), 0)
            }
        }
        return true
    }
}