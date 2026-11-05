package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflineStatusTest {
    @Test
    fun onlineNoChipShows() {
        assertThat(OfflineStatus.Online.showsChip(hasContent = true)).isFalse()
        assertThat(OfflineStatus.Online.showsChip(hasContent = false)).isFalse()
    }

    @Test
    fun offlineTheChipShowsEvenOverNothingSinceItExplainsTheShortList() {
        assertThat(OfflineStatus.ShowingDownloads.showsChip(hasContent = false)).isTrue()
        assertThat(OfflineStatus.ShowingDownloadsInOfflineMode.showsChip(hasContent = false)).isTrue()
        assertThat(OfflineStatus.ShowingDownloads.showsChip(hasContent = true)).isTrue()
    }

    @Test
    fun anUnreachableServerShowsTheChipOnlyOverCachedContent() {
        assertThat(OfflineStatus.ShowingCachedLibrary.showsChip(hasContent = true)).isTrue()
        assertThat(OfflineStatus.ShowingCachedLibrary.showsChip(hasContent = false)).isFalse()
    }
}
