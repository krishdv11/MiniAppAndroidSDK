package com.digitral.miniappsdk.state

// Internal SDK process state container.

import android.content.Context

internal object MiniAppSDKState {
    @Volatile
    internal var context: Context? = null
        private set

    @Volatile
    internal var appId: String? = null
        private set

    @Volatile
    internal var baseUrl: String? = null
        private set

    @Volatile
    internal var initialized: Boolean = false
        private set

    @Volatile
    internal var partnerId: String? = null
        private set

    @Volatile
    internal var signature: String? = null
        private set

    internal fun set(
        context: Context,
        appId: String,
        baseUrl: String,
        initialized: Boolean,
        partnerId: String,
        signature: String
    ): Unit {
        this.context = context
        this.appId = appId
        this.baseUrl = baseUrl
        this.initialized = initialized
        this.partnerId = partnerId
        this.signature = signature
    }

    internal fun clear(): Unit {
        context = null
        appId = null
        baseUrl = null
        initialized = false
        partnerId = null
        signature = null
    }
}
