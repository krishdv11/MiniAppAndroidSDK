package com.digitral.miniappsdk

import android.content.Context
import androidx.viewpager2.widget.ViewPager2
import com.digitral.miniappsdk.analytics.SDKAnalyticsTracker
import com.digitral.miniappsdk.api.MiniAppApi
import com.digitral.miniappsdk.data.MiniAppRepositoryImpl
import com.digitral.miniappsdk.domain.model.MiniAppService
import com.digitral.miniappsdk.domain.repository.MiniAppRepository
import com.digitral.miniappsdk.state.SDKState
import com.digitral.miniappsdk.ui.BannerPagerAdapter
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
        repository = MiniAppRepositoryImpl(api = api)
        trackInternalEvent("SDK_Initialized")
    }

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
        callback: (Result<Pair<List<MiniAppService>, ViewPager2>>) -> Unit
    ): Unit {
        fetchMiniAppServices { result ->
            result.onSuccess { list ->
                val pager = ViewPager2(context)
                pager.adapter = BannerPagerAdapter(list)
                callback(Result.success(Pair(list, pager)))
            }.onFailure {
                callback(Result.failure(it))
            }
        }
    }

    private fun checkInit(): Unit {
        if (!SDKState.initialized || repository == null) {
            throw IllegalStateException("Call initWithAppID(context, appId, baseUrl) first")
        }
    }

    internal fun trackInternalEvent(event: String): Unit {
        analyticsTracker?.trackEvent(event)
    }
}
