package com.digitral.miniappsdk.api

import com.digitral.miniappsdk.domain.model.MiniAppService
import retrofit2.http.GET

internal interface MiniAppApi {

    @GET("services")
    suspend fun getMiniAppServices(): List<MiniAppService>
}
