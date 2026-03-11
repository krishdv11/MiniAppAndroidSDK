package com.digitral.miniappsdk

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import com.digitral.miniappsdk.analytics.SDKAnalyticsTracker
import com.digitral.miniappsdk.api.MiniAppApi
import com.digitral.miniappsdk.data.MiniAppCacheManager
import com.digitral.miniappsdk.data.MiniAppRepositoryImpl
import com.digitral.miniappsdk.domain.model.MiniAppService
import com.digitral.miniappsdk.domain.repository.MiniAppRepository
import com.digitral.miniappsdk.state.SDKState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

public object MiniAppSDK {

    private val sdkScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repository: MiniAppRepository? = null

    @Volatile
    private var analyticsTracker: SDKAnalyticsTracker? = null

    @Synchronized
    @JvmStatic
    public fun initWithAppID(
        context: Context,
        appId: String,
        secretKey: String,
        domainUrl: String
    ): Unit {
        require(appId.isNotBlank()) { "appId cannot be blank" }
        require(secretKey.isNotBlank()) { "secretKey cannot be blank" }
        require(domainUrl.isNotBlank()) { "domainUrl cannot be blank" }
        require(domainUrl.endsWith("/")) { "domainUrl must end with '/'" }

        val appContext = context.applicationContext
        val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(domainUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api: MiniAppApi = retrofit.create(MiniAppApi::class.java)
        val cacheManager = MiniAppCacheManager(appContext)

        SDKState.set(
            context = appContext,
            appId = appId,
            baseUrl = domainUrl,
            initialized = true,
            partnerId = appId,
            signature = secretKey
        )
        analyticsTracker = SDKAnalyticsTracker(appContext)
        repository = MiniAppRepositoryImpl(
            api = api,
            appId = appId,
            secretKey = secretKey,
            domainUrl = domainUrl,
            cacheManager = cacheManager,
            httpClient = okHttpClient
        )
        trackInternalEvent("SDK_Initialized")

        // Preload runtime list and zip cache right after initialization.
        sdkScope.launch {
            try {
                repository?.syncAndCacheMiniApps()
            } catch (_: Exception) {
                // Initialization should not crash host app.
            }
        }
    }

    @JvmStatic
    public fun getCachedMiniApps(
        callback: (Result<List<MiniAppService>>) -> Unit
    ): Unit {
        checkInit()
        val currentRepository = repository ?: return callback(
            Result.failure(IllegalStateException("Repository is not initialized"))
        )

        sdkScope.launch {
            try {
                val data = currentRepository.getCachedServices()
                withContext(Dispatchers.Main) {
                    callback(Result.success(data))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    @JvmStatic
    public fun loadMiniAppInWebView(
        miniAppId: String,
        webView: WebView,
        callback: (Result<Unit>) -> Unit
    ): Unit {
        checkInit()
        val currentRepository = repository ?: return callback(
            Result.failure(IllegalStateException("Repository is not initialized"))
        )
        require(miniAppId.isNotBlank()) { "miniAppId cannot be blank" }

        sdkScope.launch {
            try {
                val sessionToken = currentRepository.getSessionToken(miniAppId)
                var entryFile = currentRepository.getCachedEntryHtml(miniAppId)
                if (entryFile == null) {
                    currentRepository.syncAndCacheMiniApps()
                    entryFile = currentRepository.getCachedEntryHtml(miniAppId)
                }
                val verifiedEntryFile = entryFile
                    ?: throw IllegalStateException("Mini app zip is not cached for $miniAppId")

                withContext(Dispatchers.Main) {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                    val loadUrl = "${verifiedEntryFile.toURI()}?sessionToken=$sessionToken"
                    webView.loadUrl(loadUrl)
                    callback(Result.success(Unit))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    private fun checkInit(): Unit {
        if (!SDKState.initialized || repository == null) {
            throw IllegalStateException("Call initWithAppID(context, appId, secretKey, domainUrl) first")
        }
    }

    internal fun trackInternalEvent(event: String): Unit {
        analyticsTracker?.trackEvent(event)
    }
}
