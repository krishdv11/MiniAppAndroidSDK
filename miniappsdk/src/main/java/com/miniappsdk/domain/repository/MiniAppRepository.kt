package com.miniappsdk.domain.repository

import com.miniappsdk.domain.model.MiniAppService

internal interface MiniAppRepository {
    suspend fun fetchServices(): List<MiniAppService>
}
