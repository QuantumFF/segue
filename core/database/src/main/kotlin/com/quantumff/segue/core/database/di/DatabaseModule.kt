package com.quantumff.segue.core.database.di

import android.content.Context
import androidx.room.Room
import com.quantumff.segue.core.database.RoomTransactionRunner
import com.quantumff.segue.core.database.SegueDatabase
import com.quantumff.segue.core.database.TransactionRunner
import com.quantumff.segue.core.database.dao.AlbumDao
import com.quantumff.segue.core.database.dao.AlbumListDao
import com.quantumff.segue.core.database.dao.ArtistDao
import com.quantumff.segue.core.database.dao.DownloadDao
import com.quantumff.segue.core.database.dao.GenreDao
import com.quantumff.segue.core.database.dao.PlaylistDao
import com.quantumff.segue.core.database.dao.QueueDao
import com.quantumff.segue.core.database.dao.ScrobbleDao
import com.quantumff.segue.core.database.dao.SearchDao
import com.quantumff.segue.core.database.dao.ServerCapabilitiesDao
import com.quantumff.segue.core.database.dao.ServerDataDao
import com.quantumff.segue.core.database.dao.SongDao
import com.quantumff.segue.core.database.dao.SyncMetadataDao
import dagger.Binds
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
    fun artistDao(db: SegueDatabase): ArtistDao = db.artistDao()

    @Provides
    fun albumDao(db: SegueDatabase): AlbumDao = db.albumDao()

    @Provides
    fun songDao(db: SegueDatabase): SongDao = db.songDao()

    @Provides
    fun genreDao(db: SegueDatabase): GenreDao = db.genreDao()

    @Provides
    fun playlistDao(db: SegueDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun albumListDao(db: SegueDatabase): AlbumListDao = db.albumListDao()

    @Provides
    fun queueDao(db: SegueDatabase): QueueDao = db.queueDao()

    @Provides
    fun scrobbleDao(db: SegueDatabase): ScrobbleDao = db.scrobbleDao()

    @Provides
    fun downloadDao(db: SegueDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun syncMetadataDao(db: SegueDatabase): SyncMetadataDao = db.syncMetadataDao()

    @Provides
    fun serverCapabilitiesDao(db: SegueDatabase): ServerCapabilitiesDao = db.serverCapabilitiesDao()

    @Provides
    fun searchDao(db: SegueDatabase): SearchDao = db.searchDao()

    @Provides
    fun serverDataDao(db: SegueDatabase): ServerDataDao = db.serverDataDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DatabaseBindingsModule {
    @Binds
    fun transactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
