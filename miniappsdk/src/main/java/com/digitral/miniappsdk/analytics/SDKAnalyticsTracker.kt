package com.digitral.miniappsdk.analytics

import android.content.Context
import android.util.Log

internal class SDKAnalyticsTracker(
    private val context: Context
) {

    private val tag: String = "MiniAppSDK-Analytics"

    fun trackEvent(event: String): Unit {
        Log.d(tag, "${context.packageName}:$event")
    }
}
