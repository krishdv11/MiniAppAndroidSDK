package com.example.miniappsampleapp

import android.content.Context
import com.digitral.miniappsdk.MiniAppSDK

object SampleSdkInitializer {
    fun init(context: Context): Result<Unit> = runCatching {
        MiniAppSDK.initWith(
            context = context.applicationContext,
            appId = "p_a1b2c3d4",
            secretKey = "partner-signature",
            domainUrl = "https://csdpdev-api.d21.co.in/"
        )
    }
}
