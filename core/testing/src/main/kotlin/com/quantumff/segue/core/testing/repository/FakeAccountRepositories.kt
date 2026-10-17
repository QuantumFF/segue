package com.quantumff.segue.core.testing.repository

import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.repository.AppUpdateRepository
import com.quantumff.segue.core.data.repository.PlaylistRepository
import com.quantumff.segue.core.data.repository.ServerCapabilitiesRepository
import com.quantumff.segue.core.data.repository.ServerRepository
import com.quantumff.segue.core.data.repository.UserPreferencesRepository
import com.quantumff.segue.core.model.AppRelease
import com.quantumff.segue.core.model.Playlist
import com.quantumff.segue.core.model.RecentSearches
import com.quantumff.segue.core.model.Server
import com.quantumff.segue.core.model.ServerCapabilities
import com.quantumff.segue.core.model.Song
import com.quantumff.segue.core.model.UserPreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeServerRepository : ServerRepository {
    val serversState = MutableStateFlow<List<Server>>(emptyList())
    val activeId = MutableStateFlow<String?>(null)

    /** Thrown by [signIn] when set. */
    var signInError: Throwable? = null
    val signIns = mutableListOf<Triple<String, String, String>>()
    var refreshCount = 0

    override val servers: Flow<ImmutableList<Server>> = serversState.map { it.toImmutableList() }

    override val activeServer: Flow<Server?> =
        combine(serversState, activeId) { servers, id -> servers.firstOrNull { it.id == id } }

    override suspend fun signIn(baseUrl: String, username: String, password: String, name: String?): Server {
        signIns += Triple(baseUrl, username, password)
        signInError?.let { throw it }
        val server =
            Server(id = "server-${signIns.size}", name = name ?: baseUrl, baseUrl = baseUrl, username = username)
        serversState.update { it + server }
        activeId.value = server.id
        return server
    }

    override suspend fun switchTo(serverId: String) {
        activeId.value = serverId
    }

    override suspend fun remove(serverId: String) {
        serversState.update { all -> all.filterNot { it.id == serverId } }
        if (activeId.value == serverId) activeId.value = serversState.value.firstOrNull()?.id
    }

    override suspend fun refreshActiveServer() {
        refreshCount++
    }
}

/** Capabilities per server id; [ServerCapabilities.Unknown] for servers not in [byServer]. */
class FakeServerCapabilitiesRepository(val byServer: MutableMap<String, ServerCapabilities> = mutableMapOf()) :
    ServerCapabilitiesRepository {
    override suspend fun forServer(serverId: String) = byServer[serverId] ?: ServerCapabilities.Unknown

    override suspend fun store(serverId: String, capabilities: ServerCapabilities) {
        byServer[serverId] = capabilities
    }

    override suspend fun refresh(serverId: String) = Unit
}

class FakePlaylistRepository : PlaylistRepository {
    val playlistsState = MutableStateFlow<List<Playlist>>(emptyList())
    val songsByPlaylist = MutableStateFlow<Map<String, List<Song>>>(emptyMap())

    /** Thrown by writes when set. */
    var error: RemoteException? = null
    val added = mutableListOf<Pair<String, List<String>>>()
    val replaced = mutableListOf<Pair<String, List<String>>>()
    val deleted = mutableListOf<String>()
    val refreshed = mutableListOf<String>()
    var refreshAllCount = 0

    override val playlists: Flow<ImmutableList<Playlist>> = playlistsState.map { it.toImmutableList() }

    override fun playlist(id: String): Flow<Playlist?> = playlistsState.map { all -> all.firstOrNull { it.id == id } }

    override fun playlistSongs(id: String): Flow<ImmutableList<Song>> =
        songsByPlaylist.map { it[id].orEmpty().toImmutableList() }

    /** Cached songs, or a refresh first (recorded in [refreshed], throwing [error]) when there are none. */
    override suspend fun songsForPlayback(id: String): ImmutableList<Song> =
        songsByPlaylist.value[id].orEmpty().ifEmpty {
            refreshPlaylist(id)
            songsByPlaylist.value[id].orEmpty()
        }.toImmutableList()

    override suspend fun refreshPlaylists() {
        refreshAllCount++
        error?.let { throw it }
    }

    override suspend fun refreshPlaylist(id: String) {
        refreshed += id
        error?.let { throw it }
    }

    override suspend fun createPlaylist(name: String, songIds: List<String>): String {
        error?.let { throw it }
        val id = "playlist-${playlistsState.value.size + 1}"
        playlistsState.update {
            it + Playlist(id, name, null, "demo", false, songIds.size, 0, null, null, null, readonly = false)
        }
        return id
    }

    override suspend fun updatePlaylist(id: String, name: String?, comment: String?, isPublic: Boolean?) {
        error?.let { throw it }
        playlistsState.update { all ->
            all.map { if (it.id == id) it.copy(name = name ?: it.name, comment = comment ?: it.comment) else it }
        }
    }

    override suspend fun addSongs(id: String, songIds: List<String>) {
        error?.let { throw it }
        added += id to songIds
    }

    override suspend fun removeSongs(id: String, positions: List<Int>) {
        error?.let { throw it }
        songsByPlaylist.update { map ->
            map + (id to map[id].orEmpty().filterIndexed { index, _ -> index !in positions })
        }
    }

    override suspend fun replaceSongs(id: String, songIds: List<String>) {
        error?.let { throw it }
        replaced += id to songIds
        songsByPlaylist.update { map ->
            val byId = map[id].orEmpty().associateBy { it.id }
            map + (id to songIds.mapNotNull { byId[it] })
        }
    }

    override suspend fun deletePlaylist(id: String) {
        error?.let { throw it }
        deleted += id
        playlistsState.update { all -> all.filterNot { it.id == id } }
    }
}

class FakeUserPreferencesRepository : UserPreferencesRepository {
    override val preferences = MutableStateFlow(UserPreferences())
    override val recentSearches = MutableStateFlow<ImmutableList<String>>(emptyList<String>().toImmutableList())

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) = preferences.update(transform)

    override suspend fun addRecentSearch(query: String) = recentSearches.update { current ->
        RecentSearches.add(current, query).toImmutableList()
    }

    override suspend fun removeRecentSearch(query: String) = recentSearches.update { current ->
        RecentSearches.remove(current, query).toImmutableList()
    }
}

class FakeAppUpdateRepository(override val installedVersion: String = "0.1.1") : AppUpdateRepository {
    /** What [check] returns: the newer release, or null when up to date. */
    var latest: AppRelease? = null
    var error: RemoteException? = null
    var checks = 0

    override suspend fun check(): AppRelease? {
        checks++
        error?.let { throw it }
        return latest
    }

    override suspend fun checkAutomatically(): AppRelease? = runCatching { check() }.getOrNull()
}
