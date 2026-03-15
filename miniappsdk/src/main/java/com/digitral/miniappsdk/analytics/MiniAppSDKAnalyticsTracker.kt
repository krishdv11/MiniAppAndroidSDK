package com.digitral.miniappsdk.analytics

// Internal lightweight analytics/event logger used by SDK internals.

import android.content.Context
import android.util.Log

internal class MiniAppSDKAnalyticsTracker(
    private val context: Context
) {

    private val tag: String = "MiniAppSDK-Analytics"

    internal fun trackEvent(event: String): Unit {
        Log.d(tag, "${context.packageName}:$event")
    }
}
