package com.digtitral.miniappsdk.api

import com.digtitral.miniappsdk.domain.model.MiniAppService
import retrofit2.http.GET
import retrofit2.http.Query

internal interface MiniAppApi {

    @GET("services")
    suspend fun getMiniAppServices(
        @Query("appId") appId: String,
        @Query("page") page: Int
    ): List<MiniAppService>
}
