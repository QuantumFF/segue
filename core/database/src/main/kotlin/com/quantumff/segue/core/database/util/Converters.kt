package com.quantumff.segue.core.database.util

import androidx.room.TypeConverter
import com.quantumff.segue.core.database.model.ArtistRefList
import com.quantumff.segue.core.database.model.DiscTitleList
import com.quantumff.segue.core.database.model.ExtensionMap
import com.quantumff.segue.core.model.DownloadState

/** Small structured values are stored as JSON text. The JSON lives in [JsonColumns]. */
internal class Converters {
    @TypeConverter
    fun artistRefsToJson(value: ArtistRefList): String = JsonColumns.encodeArtistRefs(value.items)

    @TypeConverter
    fun jsonToArtistRefs(value: String): ArtistRefList = ArtistRefList(JsonColumns.decodeArtistRefs(value))

    @TypeConverter
    fun discTitlesToJson(value: DiscTitleList): String = JsonColumns.encodeDiscTitles(value.items)

    @TypeConverter
    fun jsonToDiscTitles(value: String): DiscTitleList = DiscTitleList(JsonColumns.decodeDiscTitles(value))

    @TypeConverter
    fun extensionsToJson(value: ExtensionMap): String = JsonColumns.encodeExtensions(value.versions)

    @TypeConverter
    fun jsonToExtensions(value: String): ExtensionMap = ExtensionMap(JsonColumns.decodeExtensions(value))

    @TypeConverter
    fun downloadStateToName(value: DownloadState): String = value.name

    @TypeConverter
    fun nameToDownloadState(value: String): DownloadState =
        DownloadState.entries.firstOrNull { it.name == value } ?: DownloadState.Failed
}
