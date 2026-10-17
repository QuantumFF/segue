package com.quantumff.segue.buildlogic

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SegueVersionTest {
    @Test
    fun finalReleaseEndsIn99() {
        assertThat(SegueVersion.parse("1.2.3")).isEqualTo(SegueVersion("1.2.3", 1_020_399))
    }

    @Test
    fun preReleaseSuffixIsStageThenNumber() {
        assertThat(SegueVersion.parse("0.2.0-alpha.1").code).isEqualTo(20_011)
        assertThat(SegueVersion.parse("0.2.0-beta.3").code).isEqualTo(20_023)
        assertThat(SegueVersion.parse("0.2.0-rc").code).isEqualTo(20_030)
    }

    @Test
    fun versionCodesFollowReleaseOrder() {
        val ordered = listOf(
            "0.1.1",
            "0.1.2",
            "0.2.0-alpha",
            "0.2.0-alpha.9",
            "0.2.0-beta.1",
            "0.2.0-rc.1",
            "0.2.0-rc.2",
            "0.2.0",
            "0.2.1",
            "0.10.0",
            "1.0.0",
            "2099.99.99",
        ).map { SegueVersion.parse(it).code }

        assertThat(ordered).isInStrictOrder()
    }

    @Test
    fun releasedVersionsUpgradeOverTheHandWrittenCodes() {
        // 0.1.1 shipped with versionCode 2 before codes came from the tag.
        assertThat(SegueVersion.parse("0.1.2").code).isGreaterThan(2)
    }

    @Test
    fun rejectsVersionsTheCodeCannotOrder() {
        listOf("v1.2.3", "1.2", "1.2.3-preview.1", "1.2.3-beta.10", "1.100.0", "1.2.100", "2100.0.0").forEach {
            assertThrows(IllegalArgumentException::class.java) { SegueVersion.parse(it) }
        }
    }
}
