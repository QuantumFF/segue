package com.quantumff.segue.core.database.di

import android.content.Context
import androidx.room.Room
import com.quantumff.segue.core.database.SegueDatabase
import com.quantumff.segue.core.database.dao.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SegueDatabase =
        Room.databaseBuilder(context, SegueDatabase::class.java, "segue.db").build()

    @Provides
    fun serverDao(database: SegueDatabase): ServerDao = database.serverDao()
}
