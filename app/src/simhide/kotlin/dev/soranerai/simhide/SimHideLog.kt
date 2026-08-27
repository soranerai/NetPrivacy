package dev.soranerai.simhide

import android.util.Log
import de.robv.android.xposed.XposedBridge

internal object SimHideLog {
    private const val TAG = "SimHide"

    fun info(message: String) {
        Log.i(TAG, message)
        XposedBridge.log("$TAG: $message")
    }

    fun warn(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
        XposedBridge.log("$TAG: $message${error?.let { ": ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()}")
    }
}
