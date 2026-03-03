package com.miniappsdk.di

import com.miniappsdk.domain.repository.MiniAppRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MiniAppSdkEntryPoint {
    fun miniAppRepository(): MiniAppRepository
}
