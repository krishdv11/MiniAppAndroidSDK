package com.miniappsdk.state

internal object SDKState {
    @Volatile
    var appId: String? = null
        private set

    @Volatile
    var initialized: Boolean = false
        private set

    fun markInitialized(appId: String) {
        this.appId = appId
        initialized = true
    }

    fun clear() {
        appId = null
        initialized = false
    }
}
