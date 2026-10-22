package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueSourceRecordTest {
    @Test
    fun `every kind of source round-trips`() {
        val sources = listOf(
            QueueSource.Album("al1", "In Rainbows"),
            QueueSource.Playlist("p1", "Road trip"),
            QueueSource.Artist("ar1", "Radiohead"),
            QueueSource.Genre("Rock"),
            QueueSource.Library(shuffled = true),
            QueueSource.Library(shuffled = false),
            QueueSource.InstantMix("Nude"),
            QueueSource.Adopted("Feishin"),
            QueueSource.Unknown,
        )
        for (source in sources) {
            assertThat(QueueSourceRecord.of(source).toSource()).isEqualTo(source)
        }
    }

    @Test
    fun `each kind is stored as the same string as when it was first saved`() {
        val stored = mapOf(
            QueueSource.Album("al1", "In Rainbows") to "Album",
            QueueSource.Playlist("p1", "Road trip") to "Playlist",
            QueueSource.Artist("ar1", "Radiohead") to "Artist",
            QueueSource.Genre("Rock") to "Genre",
            QueueSource.Library(shuffled = true) to "Library",
            QueueSource.InstantMix("Nude") to "InstantMix",
            QueueSource.Adopted("Feishin") to "Adopted",
            QueueSource.Unknown to "Unknown",
        )
        for ((source, kind) in stored) {
            assertThat(QueueSourceRecord.of(source).kind.value).isEqualTo(kind)
            assertThat(QueueSourceRecord.Kind.of(kind)).isEqualTo(QueueSourceRecord.of(source).kind)
        }
    }

    @Test
    fun `a record this version cannot read is an unknown source`() {
        assertThat(record("Radio", id = "r1", name = "KEXP").toSource()).isEqualTo(QueueSource.Unknown)
        assertThat(record("Album", name = "No id").toSource()).isEqualTo(QueueSource.Unknown)
        assertThat(record("Playlist", id = "p1").toSource()).isEqualTo(QueueSource.Unknown)
        assertThat(record("Unknown", id = "x", name = "y").toSource()).isEqualTo(QueueSource.Unknown)
    }

    private fun record(kind: String, id: String? = null, name: String? = null) =
        QueueSourceRecord(QueueSourceRecord.Kind.of(kind), id, name)

    @Test
    fun `a source names the page it opens`() {
        assertThat(QueueSource.Album("al1", "In Rainbows").id).isEqualTo("al1")
        assertThat(QueueSource.Genre("Rock").id).isEqualTo("Rock")
        assertThat(QueueSource.InstantMix("Nude").id).isNull()
        assertThat(QueueSource.Adopted("Feishin").name).isEqualTo("Feishin")
        assertThat(QueueSource.Library(shuffled = true).name).isNull()
        assertThat(QueueSource.Unknown.id).isNull()
    }
}
