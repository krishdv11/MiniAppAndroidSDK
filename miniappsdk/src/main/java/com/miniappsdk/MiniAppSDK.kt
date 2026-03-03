package com.miniappsdk

import android.app.Application
import android.content.Context
import com.miniappsdk.analytics.SDKAnalytics
import com.miniappsdk.di.MiniAppRepositoryFactory
import com.miniappsdk.domain.model.MiniAppService
import com.miniappsdk.domain.repository.MiniAppRepository
import com.miniappsdk.state.SDKState
import com.miniappsdk.ui.BannerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Public entrypoint for MiniApp SDK consumers.
 */
public object MiniAppSDK {

    private const val DEFAULT_BASE_URL = "https://yourapi.com/"
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repository: MiniAppRepository? = null

    @JvmStatic
    public fun initWithAppID(application: Application, appId: String): Unit {
        initWithAppID(
            application = application,
            config = MiniAppSDKConfig(appId = appId, baseUrl = DEFAULT_BASE_URL)
        )
    }

    @JvmStatic
    public fun initWithAppID(application: Application, appId: String, baseUrl: String): Unit {
        initWithAppID(
            application = application,
            config = MiniAppSDKConfig(appId = appId, baseUrl = baseUrl)
        )
    }

    @Synchronized
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    public fun initWithAppID(application: Application, config: MiniAppSDKConfig): Unit {
        require(config.appId.isNotBlank()) { "appId cannot be blank" }
        require(config.baseUrl.isNotBlank()) { "baseUrl cannot be blank" }
        require(config.baseUrl.endsWith("/")) { "baseUrl must end with '/'" }

        repository = MiniAppRepositoryFactory.create(config.baseUrl)
        SDKState.markInitialized(config.appId)
        SDKAnalytics.trackEvent("SDK_Initialized")
    }

    @JvmStatic
    public fun isInitialized(): Boolean = SDKState.initialized

    @JvmStatic
    public fun fetchMiniAppServices(
        callback: (Result<List<MiniAppService>>) -> Unit
    ): Unit {
        checkInit()
        val currentRepository = repository ?: return callback(
            Result.failure(IllegalStateException("Repository is not initialized"))
        )

        sdkScope.launch {
            try {
                val data = currentRepository.fetchServices()
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
    public fun fetchMiniAppServicesWithUI(
        context: Context,
        callback: (Result<Pair<List<MiniAppService>, BannerView>>) -> Unit
    ): Unit {
        fetchMiniAppServices { result ->
            result.onSuccess { list ->
                val banner = BannerView(context)
                if (list.isNotEmpty()) {
                    banner.bind(list.first())
                }
                callback(Result.success(Pair(list, banner)))
            }.onFailure {
                callback(Result.failure(it))
            }
        }
    }

    @Synchronized
    @JvmStatic
    public fun shutdown(): Unit {
        repository = null
        SDKState.clear()
        SDKAnalytics.trackEvent("SDK_Shutdown")
    }

    private fun checkInit() {
        if (!SDKState.initialized || repository == null) {
            throw IllegalStateException("Call initWithAppID first")
        }
    }
}
