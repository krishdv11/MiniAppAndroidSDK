package com.miniappsdk.di

import com.miniappsdk.api.MiniAppApi
import com.miniappsdk.data.MiniAppRepositoryImpl
import com.miniappsdk.domain.repository.MiniAppRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    internal fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    internal fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://yourapi.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    internal fun provideApi(retrofit: Retrofit): MiniAppApi =
        retrofit.create(MiniAppApi::class.java)

    @Provides
    @Singleton
    internal fun provideRepository(api: MiniAppApi): MiniAppRepository =
        MiniAppRepositoryImpl(api)
}
