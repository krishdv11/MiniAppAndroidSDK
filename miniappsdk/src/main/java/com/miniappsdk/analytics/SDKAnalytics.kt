package com.miniappsdk.analytics

import android.util.Log

internal object SDKAnalytics {

    private const val TAG = "MiniAppSDK-Analytics"

    fun trackEvent(event: String) {
        Log.d(TAG, event)
        // Can be replaced with Firebase/Segment integration.
    }
}
