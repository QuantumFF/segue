package com.quantumff.segue.core.playback.browse

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Applies [BrowseCallerPolicy] to real callers: reads a caller's package, UID, install location and
 * signing certificates from the package manager. Decisions are cached per process lifetime.
 */
class BrowseCallers(private val context: Context, private val policy: BrowseCallerPolicy = BrowseCallerPolicy(Process.myUid())) {
    private val decisions = ConcurrentHashMap<Pair<String, Int>, Boolean>()

    fun isAllowed(packageName: String, uid: Int): Boolean = decisions.getOrPut(packageName to uid) {
        policy.isAllowed(identity(packageName, uid)).also { allowed ->
            if (!allowed) Log.i(TAG, "Library browsing refused for $packageName ($uid)")
        }
    }

    /** True for allowed callers that are not this app: they get a URI grant for artwork. */
    fun isExternal(uid: Int) = uid != Process.myUid()

    /** Lets [packageName] read browse artwork. */
    fun grantArtwork(packageName: String) {
        context.grantUriPermission(
            packageName,
            ArtworkProvider.rootUri(context),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
    }

    private fun identity(packageName: String, uid: Int): CallerIdentity {
        val pm = context.packageManager
        val matches = pm.getPackagesForUid(uid)?.contains(packageName) == true
        val info = try {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val flags = info?.applicationInfo?.flags ?: 0
        val signing = info?.signingInfo
        val signatures = when {
            signing == null -> emptyList()
            signing.hasMultipleSigners() -> signing.apkContentsSigners.toList()
            else -> signing.signingCertificateHistory.toList()
        }
        return CallerIdentity(
            packageName = packageName,
            uid = uid,
            packageMatchesUid = matches,
            isSystemApp = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            signingCertSha256 = signatures.mapTo(HashSet()) { sha256(it.toByteArray()) },
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }

    private companion object {
        const val TAG = "BrowseCallers"
    }
}
