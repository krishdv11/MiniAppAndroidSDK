package com.digtitral.miniappsdk.data

import com.digtitral.miniappsdk.api.MiniAppApi
import com.digtitral.miniappsdk.domain.model.MiniAppService
import com.digtitral.miniappsdk.domain.repository.MiniAppRepository

internal class MiniAppRepositoryImpl(
    private val api: MiniAppApi,
    private val appId: String
) : MiniAppRepository {

    override suspend fun fetchServices(page: Int): List<MiniAppService> {
        return retryIO {
            api.getMiniAppServices(appId = appId, page = page)
        }
    }
}
