package com.digitral.miniappsdk.domain.repository

// Internal repository contract. Not exposed to host apps.

import com.digitral.miniappsdk.domain.model.MiniAppService

import java.io.File

internal interface MiniAppRepository {
    suspend fun syncAndCacheMiniApps(): List<MiniAppService>
    fun getCachedServices(): List<MiniAppService>
    fun getCachedEntryHtml(miniAppId: String): File?
    fun getRequiredPermissions(miniAppId: String): List<String>
    suspend fun verifySessionToken(miniAppId: String): Boolean
    suspend fun recordLifecycleEvent(miniAppId: String, eventType: String, message: String = "")
}
