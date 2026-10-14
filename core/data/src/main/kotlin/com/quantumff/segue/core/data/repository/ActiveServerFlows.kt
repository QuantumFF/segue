package com.quantumff.segue.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import com.quantumff.segue.core.data.session.SubsonicClientProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Re-subscribes to [query] whenever the active server changes; emits [empty] when signed out. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> SubsonicClientProvider.forActiveServer(empty: T, query: (serverId: String) -> Flow<T>): Flow<T> =
    activeServerId.flatMapLatest { serverId -> if (serverId == null) flowOf(empty) else query(serverId) }

internal fun <E, M> SubsonicClientProvider.listForActiveServer(
    query: (serverId: String) -> Flow<List<E>>,
    mapper: (E) -> M,
): Flow<ImmutableList<M>> = forActiveServer(persistentListOf()) { serverId ->
    query(serverId).map { rows -> rows.map(mapper).toImmutableList() }
}

internal fun <E : Any, M : Any> SubsonicClientProvider.pagedForActiveServer(
    source: (serverId: String) -> PagingSource<Int, E>,
    mapper: (E) -> M,
): Flow<PagingData<M>> = forActiveServer(PagingData.empty()) { serverId ->
    Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = true)) { source(serverId) }
        .flow
        .map { page -> page.map(mapper) }
}

private const val PAGE_SIZE = 60
