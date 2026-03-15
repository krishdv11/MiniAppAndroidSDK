package com.digitral.miniappsdk.data

import android.util.Log
import com.digitral.miniappsdk.BuildConfig

// Internal verbose logger for debug SDK builds.
internal object MiniAppDebugLogger {
    private const val TAG = "MiniAppSDK-Debug"

    internal fun d(message: String) {
        if (BuildConfig.MINIAPP_VERBOSE_LOGS) {
            Log.d(TAG, message)
        }
    }

    internal fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.MINIAPP_VERBOSE_LOGS) {
            if (throwable == null) {
                Log.e(TAG, message)
            } else {
                Log.e(TAG, message, throwable)
            }
        }
    }
}
