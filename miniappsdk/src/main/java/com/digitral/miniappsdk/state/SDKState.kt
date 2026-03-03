package com.digitral.miniappsdk.state

import android.content.Context

internal object SDKState {
    @Volatile
    var context: Context? = null
        private set

    @Volatile
    var appId: String? = null
        private set

    @Volatile
    var baseUrl: String? = null
        private set

    @Volatile
    var initialized: Boolean = false
        private set

    fun set(context: Context, appId: String, baseUrl: String, initialized: Boolean): Unit {
        this.context = context
        this.appId = appId
        this.baseUrl = baseUrl
        this.initialized = initialized
    }

    fun clear(): Unit {
        context = null
        appId = null
        baseUrl = null
        initialized = false
    }
}
