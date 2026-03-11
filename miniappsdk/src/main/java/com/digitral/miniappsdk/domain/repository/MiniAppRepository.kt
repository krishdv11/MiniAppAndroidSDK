package com.digitral.miniappsdk.domain.repository

import com.digitral.miniappsdk.domain.model.MiniAppService

import java.io.File

internal interface MiniAppRepository {
    suspend fun syncAndCacheMiniApps(): List<MiniAppService>
    fun getCachedServices(): List<MiniAppService>
    suspend fun getSessionToken(miniAppId: String): String
    fun getCachedEntryHtml(miniAppId: String): File?
}
