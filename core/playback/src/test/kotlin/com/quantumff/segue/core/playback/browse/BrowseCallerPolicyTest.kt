package com.quantumff.segue.core.playback.browse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowseCallerPolicyTest {
    private val ownUid = 10_123
    private val policy = BrowseCallerPolicy(ownUid = ownUid, allowlist = BrowseCallerPolicy.DEFAULT_ALLOWLIST)

    private fun caller(
        packageName: String,
        uid: Int = 10_500,
        packageMatchesUid: Boolean = true,
        isSystemApp: Boolean = false,
        certs: Set<String> = emptySet(),
    ) = CallerIdentity(packageName, uid, packageMatchesUid, isSystemApp, certs)

    @Test
    fun `our own app is allowed`() {
        assertThat(policy.isAllowed(caller("com.quantumff.segue.debug", uid = ownUid))).isTrue()
    }

    @Test
    fun `the system process is allowed`() {
        assertThat(policy.isAllowed(caller("android", uid = BrowseCallerPolicy.SYSTEM_UID))).isTrue()
    }

    @Test
    fun `android auto is allowed when preinstalled or signed by google`() {
        assertThat(policy.isAllowed(caller(ANDROID_AUTO, isSystemApp = true))).isTrue()
        assertThat(policy.isAllowed(caller(ANDROID_AUTO, certs = setOf(BrowseCallerPolicy.GOOGLE_RELEASE_CERT))))
            .isTrue()
    }

    @Test
    fun `an app that only borrows the android auto name is rejected`() {
        assertThat(policy.isAllowed(caller(ANDROID_AUTO, certs = setOf("00".repeat(32))))).isFalse()
    }

    @Test
    fun `a package name that does not belong to the calling uid is rejected`() {
        assertThat(policy.isAllowed(caller(ANDROID_AUTO, packageMatchesUid = false, isSystemApp = true))).isFalse()
    }

    @Test
    fun `system ui and the automotive media host are allowed as system apps only`() {
        assertThat(policy.isAllowed(caller("com.android.systemui", isSystemApp = true))).isTrue()
        assertThat(policy.isAllowed(caller("com.android.systemui", isSystemApp = false))).isFalse()
        assertThat(policy.isAllowed(caller("com.android.car.media", isSystemApp = true))).isTrue()
    }

    @Test
    fun `unknown apps are rejected, even system ones`() {
        assertThat(policy.isAllowed(caller("com.example.player"))).isFalse()
        assertThat(policy.isAllowed(caller("com.vendor.preinstalled", isSystemApp = true))).isFalse()
    }

    @Test
    fun `cert digests compare case and colon insensitively`() {
        val formatted = BrowseCallerPolicy.GOOGLE_RELEASE_CERT.lowercase().chunked(2).joinToString(":")
        assertThat(policy.isAllowed(caller(ANDROID_AUTO, certs = setOf(formatted)))).isTrue()
    }

    private companion object {
        const val ANDROID_AUTO = "com.google.android.projection.gearhead"
    }
}
