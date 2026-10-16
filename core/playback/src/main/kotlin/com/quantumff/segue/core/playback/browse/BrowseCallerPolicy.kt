package com.quantumff.segue.core.playback.browse

/** What the system tells us about an app that asks for the browse tree. */
data class CallerIdentity(
    val packageName: String,
    val uid: Int,
    /** False when [packageName] is not one of the packages installed under [uid] (a spoofed name). */
    val packageMatchesUid: Boolean,
    /** Part of the system image (preinstalled). */
    val isSystemApp: Boolean,
    /** SHA-256 digests of the package's signing certificates, hex. */
    val signingCertSha256: Set<String>,
)

/** An allowed browser app: trusted when preinstalled ([trustSystemApp]) or signed with one of [certSha256]. */
data class KnownCaller(val packageName: String, val trustSystemApp: Boolean, val certSha256: Set<String> = emptySet())

/**
 * Decides who may browse and search the library through the media session. Playback controls
 * stay open to every controller (Bluetooth, watches, the notification); only the library is
 * restricted, so other apps cannot read it.
 *
 * Allowed: this app itself, the system process, and the apps in [allowlist], checked by package
 * name, owning UID, and either preinstallation or signing certificate (the same approach as
 * Media3's sample PackageValidator).
 */
class BrowseCallerPolicy(private val ownUid: Int, private val allowlist: List<KnownCaller> = DEFAULT_ALLOWLIST) {
    private val byPackage = allowlist.associateBy { it.packageName }

    fun isAllowed(caller: CallerIdentity): Boolean {
        if (caller.uid == ownUid || caller.uid == SYSTEM_UID) return true
        if (!caller.packageMatchesUid) return false
        val known = byPackage[caller.packageName] ?: return false
        if (known.trustSystemApp && caller.isSystemApp) return true
        val certs = caller.signingCertSha256.mapTo(HashSet(), ::normalize)
        return known.certSha256.any { normalize(it) in certs }
    }

    companion object {
        /** `android.os.Process.SYSTEM_UID`. */
        const val SYSTEM_UID = 1000

        /** Google's release signing certificate, which signs Android Auto (and Play services). */
        const val GOOGLE_RELEASE_CERT = "F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83"

        val DEFAULT_ALLOWLIST = listOf(
            // Android Auto: preinstalled from Android 10, a Play Store app before.
            KnownCaller("com.google.android.projection.gearhead", trustSystemApp = true, setOf(GOOGLE_RELEASE_CERT)),
            // System UI media controls and their playback resumption.
            KnownCaller("com.android.systemui", trustSystemApp = true),
            // Android Automotive OS media center and launcher.
            KnownCaller("com.android.car.media", trustSystemApp = true),
            KnownCaller("com.android.car.carlauncher", trustSystemApp = true),
        )

        private fun normalize(digest: String) = digest.replace(":", "").uppercase()
    }
}
