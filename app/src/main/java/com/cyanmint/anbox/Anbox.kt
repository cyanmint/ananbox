package com.cyanmint.anbox

import android.os.Build
import android.os.Parcel
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

object Anbox: View.OnTouchListener {
    init {
        System.loadLibrary("anbox")
    }

    external fun stringFromJNI(): String
    external fun setPath(path: String)
    external fun startRuntime()
    external fun destroyWindow()
    external fun stopRuntime()
    /**
     * Forks & execs [cmd], redirecting its stdout/stderr to a pipe whose
     * read end file descriptor is returned so the caller can stream the
     * executed command's output (e.g. into the app's log box).
     */
    external fun startContainer(cmd: String): Int
    /**
     * Kills the container process (and its whole process group, i.e. the
     * launch script, the backgrounded proot process and the guest init it
     * ptraces) started by [startContainer], if one is still running. Safe to
     * call even if no container is running.
     */
    external fun stopContainer()
    external fun resetWindow(height: Int, width: Int)
    external fun createSurface(surface: Surface)
    external fun destroySurface()
    // pipe including Renderer, GPS & Sensor, input manager
    external fun initRuntime(width: Int, height: Int, dpi: Int): Boolean
    external fun pushFingerUp(i: Int)
    external fun pushFingerDown(x: Int, y: Int, fingerId: Int)
    external fun pushFingerMotion(x: Int, y: Int, fingerId: Int)
    external fun dumpParcel(parcel: Parcel, path: String)

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

    fun getBroadcastIntentTransactionCode(): Int {
        var code = 0
        val cActivityManager = Class.forName("android.app.ActivityManager")
        val mGetService = cActivityManager.getMethod("getService")
        val oActivityManager = mGetService.invoke(null)
        val stubClass = oActivityManager.javaClass
        val binderField = stubClass.getDeclaredField("mRemote")
        binderField.isAccessible = true
        val activityManagerBinder = binderField.get(oActivityManager)
        val binderClass = Class.forName("android.os.IBinder")
        val binderProxy = Proxy.newProxyInstance(binderClass.classLoader, arrayOf(binderClass),
            InvocationHandler { any, method, anies ->
                code = anies.get(0) as Int
                true
            })
        val broadcastMethod = stubClass.methods.find { it.name.equals("broadcastIntent") }
        binderField.set(oActivityManager, binderProxy)
        broadcastMethod?.invoke(oActivityManager, null, null, null, null, 0, null,
            null, null, 0, null, false, false, 0)
        binderField.set(oActivityManager, activityManagerBinder)
        return code
    }
}