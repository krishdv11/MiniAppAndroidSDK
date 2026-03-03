package com.digtitral.miniappsdk.api

import com.digtitral.miniappsdk.domain.model.MiniAppService
import retrofit2.http.GET

internal interface MiniAppApi {

    @GET("services")
    suspend fun getMiniAppServices(): List<MiniAppService>
}
