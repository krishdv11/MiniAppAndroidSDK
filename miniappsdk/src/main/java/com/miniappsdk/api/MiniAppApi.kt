package com.miniappsdk.api

import com.miniappsdk.domain.model.MiniAppService
import retrofit2.http.GET
import retrofit2.http.Query

internal interface MiniAppApi {

    @GET("services")
    suspend fun getMiniAppServices(
        @Query("appId") appId: String
    ): List<MiniAppService>
}
