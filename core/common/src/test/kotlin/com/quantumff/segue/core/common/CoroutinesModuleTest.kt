package com.quantumff.segue.core.common

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.common.di.CoroutinesModule
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class CoroutinesModuleTest {
    @Test
    fun `IO qualifier maps to Dispatchers IO`() {
        assertThat(CoroutinesModule.ioDispatcher()).isSameInstanceAs(Dispatchers.IO)
    }
}
