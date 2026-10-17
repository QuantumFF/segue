package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.datastore.UserPreferencesDataSource
import com.quantumff.segue.core.model.UserPreferences
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App settings, so features do not depend on core:datastore. */
interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    /**
     * Applies [transform] to the current preferences and persists the result atomically, e.g.
     * `update { it.copy(themeMode = ThemeMode.Dark) }`.
     */
    suspend fun update(transform: (UserPreferences) -> UserPreferences)

    /** Recent search queries, newest first. */
    val recentSearches: Flow<ImmutableList<String>>

    /** See [com.quantumff.segue.core.model.RecentSearches.add]. */
    suspend fun addRecentSearch(query: String)

    suspend fun removeRecentSearch(query: String)
}

internal class DataStoreUserPreferencesRepository @Inject constructor(private val source: UserPreferencesDataSource) :
    UserPreferencesRepository {
    override val preferences: Flow<UserPreferences> = source.preferences

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) = source.update(transform)

    override val recentSearches: Flow<ImmutableList<String>> = source.recentSearches.map { it.toImmutableList() }

    override suspend fun addRecentSearch(query: String) = source.addRecentSearch(query)

    override suspend fun removeRecentSearch(query: String) = source.removeRecentSearch(query)
}
