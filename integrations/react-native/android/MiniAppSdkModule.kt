package com.digitral.miniappsdk.reactnative

import com.digitral.miniappsdk.MiniAppSDK
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

// Minimal React Native bridge skeleton for MiniAppSDK Android integration.
class MiniAppSdkModule(
    reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "MiniAppSdkModule"

    @ReactMethod
    fun initMiniAppSDK(appId: String, secretKey: String, domainUrl: String, promise: Promise) {
        try {
            MiniAppSDK.initWith(reactApplicationContext, appId, secretKey, domainUrl)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("INIT_FAILED", e)
        }
    }

    @ReactMethod
    fun fetchMiniApps(promise: Promise) {
        MiniAppSDK.fetchMiniApps { result ->
            result.onSuccess { services ->
                val arr = Arguments.createArray()
                services.forEach { service ->
                    val map = Arguments.createMap()
                    map.putString("id", service.id)
                    map.putString("title", service.title)
                    map.putString("description", service.description)
                    map.putString("imageUrl", service.imageUrl)
                    arr.pushMap(map)
                }
                promise.resolve(arr)
            }.onFailure { error ->
                promise.reject("FETCH_MINIAPPS_FAILED", error)
            }
        }
    }

    @ReactMethod
    fun openMiniApp(miniAppId: String, promise: Promise) {
        MiniAppSDK.openMiniApp(miniAppId) { result ->
            result.onSuccess {
                promise.resolve(true)
            }.onFailure { error ->
                promise.reject("OPEN_MINIAPP_FAILED", error)
            }
        }
    }
}
