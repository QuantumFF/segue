package com.quantumff.segue.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumff.segue.core.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A playlist in the expanded sidebar's list. */
@Immutable
data class SidebarPlaylist(val id: String, val name: String)

/** The expanded sidebar's "Playlists" list (DESIGN.md §3), from Room. */
@HiltViewModel
class SidebarViewModel @Inject constructor(playlists: PlaylistRepository) : ViewModel() {
    val playlists: StateFlow<ImmutableList<SidebarPlaylist>> = playlists.playlists
        .map { list -> list.map { SidebarPlaylist(it.id, it.name) }.toImmutableList() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), persistentListOf())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
