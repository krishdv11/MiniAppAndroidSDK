package com.digitral.miniappsdk.data

// Internal retry helper for transient IO/network operations.

import java.io.IOException
import kotlinx.coroutines.delay

internal suspend fun <T> miniAppRetryIO(
    times: Int = 3,
    block: suspend () -> T
): T {
    require(times > 0) { "times must be > 0" }
    var attempt = 0
    var lastException: IOException? = null

    while (attempt < times) {
        try {
            return block()
        } catch (io: IOException) {
            lastException = io
            attempt++
            if (attempt >= times) {
                break
            }
            delay(1_000)
        }
    }

    throw lastException ?: IOException("miniAppRetryIO failed without IOException context")
}
