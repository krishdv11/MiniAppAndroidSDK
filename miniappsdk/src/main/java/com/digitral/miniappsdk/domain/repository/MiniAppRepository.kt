package com.digitral.miniappsdk.domain.repository

import com.digitral.miniappsdk.domain.model.MiniAppService

internal interface MiniAppRepository {
    suspend fun fetchServices(): List<MiniAppService>
}
