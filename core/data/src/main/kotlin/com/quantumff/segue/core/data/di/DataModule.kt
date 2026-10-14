package com.quantumff.segue.core.data.di

import com.quantumff.segue.core.data.repository.DataStoreServerRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun serverRepository(impl: DataStoreServerRepository): ServerRepository
}
