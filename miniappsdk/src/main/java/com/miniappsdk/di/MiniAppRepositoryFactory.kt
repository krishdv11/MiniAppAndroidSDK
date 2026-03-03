package com.miniappsdk.di

import com.miniappsdk.api.MiniAppApi
import com.miniappsdk.data.MiniAppRepositoryImpl
import com.miniappsdk.domain.repository.MiniAppRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal object MiniAppRepositoryFactory {

    internal fun create(baseUrl: String): MiniAppRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(MiniAppApi::class.java)
        return MiniAppRepositoryImpl(api)
    }
}
