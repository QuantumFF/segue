package com.quantumff.segue.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StorageLimitTest {
    @Test
    fun `no limit is never reached`() {
        assertThat(StorageLimit.None.isReachedBy(Long.MAX_VALUE)).isFalse()
        assertThat(StorageLimit.None.bytes).isNull()
    }

    @Test
    fun `a limit is reached once downloads use that much, in decimal gigabytes`() {
        assertThat(StorageLimit.Gb2.bytes).isEqualTo(2_000_000_000L)
        assertThat(StorageLimit.Gb2.isReachedBy(1_999_999_999L)).isFalse()
        assertThat(StorageLimit.Gb2.isReachedBy(2_000_000_000L)).isTrue()
        assertThat(StorageLimit.Gb20.isReachedBy(5_000_000_000L)).isFalse()
    }

    @Test
    fun `the choices are none, 2, 5, 10 and 20 GB`() {
        assertThat(StorageLimit.entries.map { it.gigabytes }).containsExactly(null, 2, 5, 10, 20).inOrder()
    }
}
