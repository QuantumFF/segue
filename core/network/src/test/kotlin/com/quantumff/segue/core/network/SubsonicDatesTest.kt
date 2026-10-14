package com.quantumff.segue.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubsonicDatesTest {
    @Test
    fun `parses UTC timestamps with and without fractions`() {
        assertThat(SubsonicDates.toEpochMillis("2024-05-01T08:00:00Z")).isEqualTo(1_714_550_400_000)
        assertThat(SubsonicDates.toEpochMillis("2024-05-01T08:00:00.123456789Z")).isEqualTo(1_714_550_400_123)
    }

    @Test
    fun `parses timestamps with an offset`() {
        assertThat(SubsonicDates.toEpochMillis("2024-05-01T10:00:00+02:00")).isEqualTo(1_714_550_400_000)
    }

    @Test
    fun `Navidrome zero time, blanks, and garbage are null`() {
        assertThat(SubsonicDates.toEpochMillis("0001-01-01T00:00:00Z")).isNull()
        assertThat(SubsonicDates.toEpochMillis("")).isNull()
        assertThat(SubsonicDates.toEpochMillis(null)).isNull()
        assertThat(SubsonicDates.toEpochMillis("yesterday")).isNull()
    }
}
