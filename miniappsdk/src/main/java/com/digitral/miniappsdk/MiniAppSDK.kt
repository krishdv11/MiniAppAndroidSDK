package com.digitral.miniappsdk

// Public SDK surface used by host applications.

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.viewpager2.widget.ViewPager2
import com.digitral.miniappsdk.analytics.MiniAppSDKAnalyticsTracker
import com.digitral.miniappsdk.api.MiniAppApi
import com.digitral.miniappsdk.data.MiniAppCacheManager
import com.digitral.miniappsdk.data.MiniAppDebugLogger
import com.digitral.miniappsdk.data.MiniAppRepositoryImpl
import com.digitral.miniappsdk.domain.model.MiniAppService
import com.digitral.miniappsdk.domain.repository.MiniAppRepository
import com.digitral.miniappsdk.state.MiniAppSDKState
import com.digitral.miniappsdk.ui.MiniAppBannerPagerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

public object MiniAppSDK {

    private val sdkScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repository: MiniAppRepository? = null

    @Volatile
    private var analyticsTracker: MiniAppSDKAnalyticsTracker? = null

    @Synchronized
    @JvmStatic
    // Public framework entrypoint.
    public fun initWith(
        context: Context,
        appId: String,
        secretKey: String,
        domainUrl: String
    ): Unit {
        require(appId.isNotBlank()) { "appId cannot be blank" }
        require(secretKey.isNotBlank()) { "secretKey cannot be blank" }
        require(domainUrl.isNotBlank()) { "domainUrl cannot be blank" }
        require(domainUrl.endsWith("/")) { "domainUrl must end with '/'" }
        MiniAppDebugLogger.d("initWith called. appId=$appId domainUrl=$domainUrl")

        val appContext = context.applicationContext
        val okHttpBuilder = OkHttpClient.Builder()
        if (BuildConfig.MINIAPP_VERBOSE_LOGS) {
            val networkLogInterceptor = HttpLoggingInterceptor { message ->
                MiniAppDebugLogger.d("HTTP: $message")
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val timingInterceptor = Interceptor { chain ->
                val request = chain.request()
                val startNs = System.nanoTime()
                MiniAppDebugLogger.d("API Request -> ${request.method} ${request.url}")
                try {
                    val response = chain.proceed(request)
                    val tookMs = (System.nanoTime() - startNs) / 1_000_000
                    MiniAppDebugLogger.d(
                        "API Response <- ${response.code} ${request.method} ${request.url} (${tookMs}ms)"
                    )
                    response
                } catch (e: Exception) {
                    val tookMs = (System.nanoTime() - startNs) / 1_000_000
                    MiniAppDebugLogger.e(
                        "API Error <- ${request.method} ${request.url} (${tookMs}ms): ${e.message}",
                        e
                    )
                    throw e
                }
            }
            okHttpBuilder.addInterceptor(timingInterceptor)
            okHttpBuilder.addInterceptor(networkLogInterceptor)
            MiniAppDebugLogger.d("Verbose SDK logging enabled for debug artifact.")
        }
        val okHttpClient: OkHttpClient = okHttpBuilder.build()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(domainUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api: MiniAppApi = retrofit.create(MiniAppApi::class.java)
        val cacheManager = MiniAppCacheManager(appContext)

        MiniAppSDKState.set(
            context = appContext,
            appId = appId,
            baseUrl = domainUrl,
            initialized = true,
            partnerId = appId,
            signature = secretKey
        )
        analyticsTracker = MiniAppSDKAnalyticsTracker(appContext)
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
                MiniAppDebugLogger.d("Background syncAndCacheMiniApps completed")
            } catch (_: Exception) {
                // Initialization should not crash host app.
                MiniAppDebugLogger.d("Background syncAndCacheMiniApps failed (suppressed)")
            }
        }
    }

    @Synchronized
    @JvmStatic
    // Public alias to match host naming conventions.
    public fun initMiniAppSDK(
        context: Context,
        appId: String,
        secretKey: String,
        domainUrl: String
    ): Unit = initWith(context, appId, secretKey, domainUrl)

    @JvmStatic
    // Public API: fetch mini app metadata list.
    public fun fetchMiniApps(
        callback: (Result<List<MiniAppService>>) -> Unit
    ): Unit {
        checkInit()
        MiniAppDebugLogger.d("fetchMiniApps called")
        val currentRepository = repository ?: return callback(
            Result.failure(IllegalStateException("Repository is not initialized"))
        )

        sdkScope.launch {
            try {
                var data = currentRepository.getCachedServices()
                if (data.isEmpty()) {
                    data = currentRepository.syncAndCacheMiniApps()
                }
                MiniAppDebugLogger.d("fetchMiniApps success count=${data.size}")
                withContext(Dispatchers.Main) {
                    callback(Result.success(data))
                }
            } catch (e: Exception) {
                MiniAppDebugLogger.e("fetchMiniApps failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    @JvmStatic
    // Public API: fetch mini app list and return SDK-provided default UI.
    public fun fetchMiniAppsWithUI(
        callback: (Result<View>) -> Unit
    ): Unit {
        fetchMiniApps { result ->
            result.onSuccess { services ->
                try {
                    val context = MiniAppSDKState.context
                        ?: throw IllegalStateException("SDK context is not available")
                    if (services.isEmpty()) {
                        callback(Result.failure(IllegalStateException("No mini apps available to render")))
                        return@onSuccess
                    }
                    val viewPager = ViewPager2(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            context.resources.getDimensionPixelSize(R.dimen.miniapp_banner_min_height)
                        )
                        adapter = MiniAppBannerPagerAdapter(services)
                    }
                    callback(Result.success(viewPager))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }.onFailure { error ->
                callback(Result.failure(error))
            }
        }
    }

    @JvmStatic
    // Public API: open mini app from extracted local cache in WebView.
    public fun openApp(
        miniAppId: String,
        callback: (Result<Unit>) -> Unit
    ): Unit {
        checkInit()
        require(miniAppId.isNotBlank()) { "miniAppId cannot be blank" }
        MiniAppDebugLogger.d("openApp(container) called miniAppId=$miniAppId")
        val currentRepository = repository ?: return callback(
            Result.failure(IllegalStateException("Repository is not initialized"))
        )
        if (currentRepository.getCachedEntryHtml(miniAppId) == null) {
            MiniAppDebugLogger.d("openApp(container) blocked: Download In Progress for $miniAppId")
            callback(Result.failure(IllegalStateException("Download In Progress")))
            return
        }
        val context = MiniAppSDKState.context ?: return callback(
            Result.failure(IllegalStateException("SDK context is not available"))
        )
        try {
            val miniAppTitle = currentRepository.getCachedServices()
                .firstOrNull { it.id == miniAppId }
                ?.title
                ?.takeIf { it.isNotBlank() }
                ?: miniAppId
            val miniAppIconUrl = currentRepository.getCachedServices()
                .firstOrNull { it.id == miniAppId }
                ?.imageUrl
                ?.takeIf { it.isNotBlank() }
            val intent = Intent(context, MiniAppHostActivity::class.java).apply {
                putExtra(MiniAppHostActivity.EXTRA_MINI_APP_ID, miniAppId)
                putExtra(MiniAppHostActivity.EXTRA_MINI_APP_TITLE, miniAppTitle)
                putExtra(MiniAppHostActivity.EXTRA_MINI_APP_ICON_URL, miniAppIconUrl)
                data = Uri.parse("miniappsdk://open/$miniAppId")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                )
            }
            context.startActivity(intent)
            MiniAppDebugLogger.d("openApp(container) launched miniAppId=$miniAppId")
            callback(Result.success(Unit))
        } catch (e: Exception) {
            MiniAppDebugLogger.e("openApp(container) failed miniAppId=$miniAppId: ${e.message}", e)
            callback(Result.failure(e))
        }
    }

    @JvmStatic
    // Public API: open mini app directly in host-provided WebView.
    public fun openApp(
        miniAppId: String,
        webView: WebView,
        callback: (Result<Unit>) -> Unit
    ): Unit {
        checkInit()
        val currentRepository = repository ?: return callback(
            Result.failure(IllegalStateException("Repository is not initialized"))
        )
        require(miniAppId.isNotBlank()) { "miniAppId cannot be blank" }
        MiniAppDebugLogger.d("openApp(webView) called miniAppId=$miniAppId")

        sdkScope.launch {
            try {
                val sessionVerified = currentRepository.verifySessionToken(miniAppId)
                if (!sessionVerified) {
                    throw IllegalStateException("Session token verification failed")
                }
                val verifiedEntryFile = currentRepository.getCachedEntryHtml(miniAppId)
                    ?: throw IllegalStateException("Download In Progress")

                withContext(Dispatchers.Main) {
                    configureLocalMiniAppWebView(webView.settings)
                    val loadUrl = verifiedEntryFile.toURI().toString()
                    webView.loadUrl(loadUrl)
                    MiniAppDebugLogger.d("openApp(webView) loaded url=$loadUrl")
                    callback(Result.success(Unit))
                }
            } catch (e: Exception) {
                MiniAppDebugLogger.e("openApp(webView) failed miniAppId=$miniAppId: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    @JvmStatic
    // Public alias to match host naming conventions.
    public fun openMiniApp(
        miniAppId: String,
        callback: (Result<Unit>) -> Unit
    ): Unit = openApp(miniAppId, callback)

    @JvmStatic
    // Public alias to match host naming conventions.
    public fun openMiniApp(
        miniAppId: String,
        webView: WebView,
        callback: (Result<Unit>) -> Unit
    ): Unit = openApp(miniAppId, webView, callback)

    private fun checkInit(): Unit {
        if (!MiniAppSDKState.initialized || repository == null) {
            throw IllegalStateException("Call initWith(context, appId, secretKey, domainUrl) first")
        }
    }

    internal fun trackInternalEvent(event: String): Unit {
        analyticsTracker?.trackEvent(event)
    }

    internal fun getRequiredPermissions(miniAppId: String): List<String> {
        val currentRepository = repository ?: return emptyList()
        return currentRepository.getRequiredPermissions(miniAppId)
    }

    internal suspend fun trackLifecycleEvent(
        miniAppId: String,
        eventType: String,
        message: String = ""
    ) {
        val currentRepository = repository ?: return
        currentRepository.recordLifecycleEvent(miniAppId, eventType, message)
    }

    internal suspend fun verifySessionToken(miniAppId: String): Boolean {
        val currentRepository = repository ?: return false
        return currentRepository.verifySessionToken(miniAppId)
    }

    private fun configureLocalMiniAppWebView(settings: WebSettings): Unit {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        enableLegacyFileUrlAccess(settings)
    }

    @Suppress("DEPRECATION")
    private fun enableLegacyFileUrlAccess(settings: WebSettings): Unit {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = true
        }
    }
}
