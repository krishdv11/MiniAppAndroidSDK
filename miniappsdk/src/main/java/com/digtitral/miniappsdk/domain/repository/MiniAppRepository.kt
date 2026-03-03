package com.digtitral.miniappsdk.domain.repository

import com.digtitral.miniappsdk.domain.model.MiniAppService

internal interface MiniAppRepository {
    suspend fun fetchServices(page: Int): List<MiniAppService>
}
