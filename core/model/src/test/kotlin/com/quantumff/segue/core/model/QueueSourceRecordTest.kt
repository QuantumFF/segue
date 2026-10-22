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
    fun `a record this version cannot read is an unknown source`() {
        assertThat(QueueSourceRecord("Radio", id = "r1", name = "KEXP").toSource()).isEqualTo(QueueSource.Unknown)
        assertThat(QueueSourceRecord("Album", name = "No id").toSource()).isEqualTo(QueueSource.Unknown)
        assertThat(QueueSourceRecord("Playlist", id = "p1").toSource()).isEqualTo(QueueSource.Unknown)
    }

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
