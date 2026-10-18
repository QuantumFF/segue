package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.quantumff.segue.core.datastore.UserPreferencesDataSource.Keys

/**
 * Replaces the four named grid card sizes with the columns they added to the usual count:
 * ExtraSmall two, Small one, Medium none, Large one fewer. An unknown name is dropped.
 */
internal object GridSizeMigration : DataMigration<Preferences> {
    private val legacy = mapOf(
        Keys.LegacyAlbumGridSize to Keys.AlbumGridColumns,
        Keys.LegacyArtistGridSize to Keys.ArtistGridColumns,
    )
    private val extraColumns = mapOf("ExtraSmall" to 2, "Small" to 1, "Medium" to 0, "Large" to -1)

    override suspend fun shouldMigrate(currentData: Preferences) = legacy.keys.any { it in currentData }

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        legacy.forEach { (old, new) ->
            val columns = remove(old)?.let(extraColumns::get) ?: return@forEach
            if (new !in this) this[new] = columns
        }
    }.toPreferences()

    override suspend fun cleanUp() = Unit
}
