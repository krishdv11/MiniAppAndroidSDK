package com.miniappsdk.data

import com.miniappsdk.api.MiniAppApi
import com.miniappsdk.domain.model.MiniAppService
import com.miniappsdk.domain.repository.MiniAppRepository
import com.miniappsdk.state.SDKState
import javax.inject.Inject

internal class MiniAppRepositoryImpl @Inject constructor(
    private val api: MiniAppApi
) : MiniAppRepository {

    override suspend fun fetchServices(): List<MiniAppService> {
        return api.getMiniAppServices(SDKState.appId.orEmpty())
    }
}
