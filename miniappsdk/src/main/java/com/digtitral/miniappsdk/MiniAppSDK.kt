package com.digtitral.miniappsdk

import android.content.Context
import android.widget.LinearLayout
import com.digtitral.miniappsdk.analytics.SDKAnalyticsTracker
import com.digtitral.miniappsdk.api.MiniAppApi
import com.digtitral.miniappsdk.data.MiniAppRepositoryImpl
import com.digtitral.miniappsdk.domain.model.MiniAppService
import com.digtitral.miniappsdk.domain.repository.MiniAppRepository
import com.digtitral.miniappsdk.state.SDKState
import com.digtitral.miniappsdk.ui.BannerView
import androidx.viewpager2.widget.ViewPager2
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
        baseUrl: String
    ): Unit {
        require(appId.isNotBlank()) { "appId cannot be blank" }
        require(baseUrl.isNotBlank()) { "baseUrl cannot be blank" }
        require(baseUrl.endsWith("/")) { "baseUrl must end with '/'" }

        val appContext = context.applicationContext
        val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api: MiniAppApi = retrofit.create(MiniAppApi::class.java)

        SDKState.set(context = appContext, appId = appId, baseUrl = baseUrl, initialized = true)
        analyticsTracker = SDKAnalyticsTracker(appContext)
        repository = MiniAppRepositoryImpl(api = api, appId = appId)
        trackInternalEvent("SDK_Initialized")
    }

    @JvmStatic
    public fun fetchMiniAppServices(
        page: Int = 1,
        callback: (Result<List<MiniAppService>>) -> Unit
    ): Unit {
        require(page > 0) { "page must be > 0" }
        checkInit()
        val currentRepository = repository ?: return callback(
            Result.failure(IllegalStateException("Repository is not initialized"))
        )

        sdkScope.launch {
            try {
                val data = currentRepository.fetchServices(page)
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
        page: Int = 1,
        callback: (Result<Pair<List<MiniAppService>, BannerView>>) -> Unit
    ): Unit {
        fetchMiniAppServices(page = page) { result ->
            result.onSuccess { list ->
                val banner = BannerView(context)
                if (list.isEmpty()) {
                    callback(Result.success(Pair(list, banner)))
                    return@onSuccess
                }

                val viewPager = ViewPager2(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    adapter = com.digtitral.miniappsdk.ui.BannerPagerAdapter(list)
                }
                banner.bindPager(items = list, viewPager = viewPager)
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
        trackInternalEvent("SDK_Shutdown")
        analyticsTracker = null
    }

    @JvmStatic
    public fun isInitialized(): Boolean = SDKState.initialized

    private fun checkInit(): Unit {
        if (!SDKState.initialized || repository == null) {
            throw IllegalStateException("Call initWithAppID(context, appId, baseUrl) first")
        }
    }

    internal fun trackInternalEvent(event: String): Unit {
        analyticsTracker?.trackEvent(event)
    }
}
