package com.digitral.miniappsdk.api

// Internal Retrofit API definitions used by SDK data layer.

import com.google.gson.JsonElement
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST

internal interface MiniAppApi {

    @POST("miniapp/v1/partner/auth")
    suspend fun partnerAuth(
        @Body request: PartnerAuthRequest
    ): ApiEnvelope<PartnerAuthData>

    @POST("miniapp/v1/runtime/list")
    suspend fun getRuntimeMiniApps(
        @Header("Authorization") authorization: String?
    ): ApiEnvelope<List<RuntimeMiniAppDto>>

    @POST("miniapp/v1/runtime/{appId}/download-token")
    suspend fun getDownloadToken(
        @Header("Authorization") authorization: String?,
        @Path("appId") appId: String,
        @Body request: DownloadTokenRequest
    ): ApiEnvelope<DownloadTokenData>

    @POST("miniapp/v1/runtime/{appId}/session-token")
    suspend fun getSessionToken(
        @Header("Authorization") authorization: String?,
        @Path("appId") appId: String,
        @Body request: SessionTokenRequest
    ): ApiEnvelope<SessionTokenData>

    @POST("miniapp/v1/metrics/events")
    suspend fun recordEvent(
        @Header("Authorization") authorization: String?,
        @Body request: MetricsEventRequest
    ): ApiEnvelope<JsonElement>
}
