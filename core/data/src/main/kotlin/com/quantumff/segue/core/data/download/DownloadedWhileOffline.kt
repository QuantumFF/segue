package com.quantumff.segue.core.data.download

import com.quantumff.segue.core.model.DownloadedLibrary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * What is downloaded while offline, and null while online: what browse lists narrow to (see
 * [com.quantumff.segue.core.model.OfflineLibrary]). The downloads are observed only while offline.
 * Screens read it through [OfflineLibraryView.downloadedWhileOffline].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun OfflineModeRepository.downloadedWhileOffline(downloads: DownloadRepository): Flow<DownloadedLibrary?> =
    isOffline.flatMapLatest { offline -> if (offline) downloads.downloadedLibrary else flowOf(null) }
