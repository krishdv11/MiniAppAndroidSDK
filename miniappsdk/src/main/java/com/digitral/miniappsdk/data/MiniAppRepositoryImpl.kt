package com.digitral.miniappsdk.data

import com.digitral.miniappsdk.api.MiniAppApi
import com.digitral.miniappsdk.domain.model.MiniAppService
import com.digitral.miniappsdk.domain.repository.MiniAppRepository

internal class MiniAppRepositoryImpl(
    private val api: MiniAppApi
) : MiniAppRepository {

    override suspend fun fetchServices(): List<MiniAppService> {
        return retryIO {
            api.getMiniAppServices()
        }
    }
}
