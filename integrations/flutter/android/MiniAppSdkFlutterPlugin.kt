package com.digitral.miniappsdk.flutter

import android.content.Context
import com.digitral.miniappsdk.MiniAppSDK
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

// Minimal Flutter plugin skeleton for MiniAppSDK Android integration.
class MiniAppSdkFlutterPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var appContext: Context

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "miniappsdk")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initMiniAppSDK" -> {
                val appId = call.argument<String>("appId").orEmpty()
                val secretKey = call.argument<String>("secretKey").orEmpty()
                val domainUrl = call.argument<String>("domainUrl").orEmpty()
                try {
                    MiniAppSDK.initWith(appContext, appId, secretKey, domainUrl)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("INIT_FAILED", e.message, null)
                }
            }

            "fetchMiniApps" -> {
                MiniAppSDK.fetchMiniApps { fetchResult ->
                    fetchResult.onSuccess { services ->
                        val response = services.map {
                            mapOf(
                                "id" to it.id,
                                "title" to it.title,
                                "description" to it.description,
                                "imageUrl" to it.imageUrl
                            )
                        }
                        result.success(response)
                    }.onFailure { error ->
                        result.error("FETCH_MINIAPPS_FAILED", error.message, null)
                    }
                }
            }

            "openMiniApp" -> {
                val miniAppId = call.argument<String>("miniAppId").orEmpty()
                MiniAppSDK.openMiniApp(miniAppId) { openResult ->
                    openResult.onSuccess {
                        result.success(true)
                    }.onFailure { error ->
                        result.error("OPEN_MINIAPP_FAILED", error.message, null)
                    }
                }
            }

            else -> result.notImplemented()
        }
    }
}
