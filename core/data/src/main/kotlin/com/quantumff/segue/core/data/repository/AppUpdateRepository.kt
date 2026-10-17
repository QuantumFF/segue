package com.quantumff.segue.core.data.repository

import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.data.RemoteException
import com.quantumff.segue.core.data.util.Clock
import com.quantumff.segue.core.datastore.UserPreferencesDataSource
import com.quantumff.segue.core.model.AppRelease
import com.quantumff.segue.core.network.GitHubReleaseClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Whether a newer Segue is out, from the GitHub releases. */
interface AppUpdateRepository {
    /** The installed app's version name, e.g. "0.1.1". */
    val installedVersion: String

    /** The latest release if it is newer than the installed version, else null. Throws RemoteException. */
    suspend fun check(): AppRelease?

    /**
     * [check], run on app start: only while the checkForUpdates setting is on, and at most once a
     * day. A failed check is retried on the next start. Null when it did not run, failed, or found
     * nothing newer.
     */
    suspend fun checkAutomatically(): AppRelease?
}

/** The installed app's version name, e.g. "0.1.1". */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class InstalledVersionName

internal class GitHubAppUpdateRepository @Inject constructor(
    private val releases: GitHubReleaseClient,
    private val preferences: UserPreferencesDataSource,
    private val clock: Clock,
    @InstalledVersionName override val installedVersion: String,
    @Dispatcher(SegueDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : AppUpdateRepository {
    override suspend fun check(): AppRelease? {
        val latest = withContext(ioDispatcher) {
            try {
                releases.latest()
            } catch (e: IOException) {
                throw RemoteException.Unreachable(e)
            }
        }
        val version = latest.tagName.removePrefix("v")
        return AppRelease(version, latest.htmlUrl).takeIf { isNewerVersion(version, installedVersion) }
    }

    override suspend fun checkAutomatically(): AppRelease? {
        if (!preferences.preferences.first().checkForUpdates) return null
        val now = clock.nowMillis()
        val last = preferences.lastUpdateCheckMillis()
        // A clock set back since the last check counts as due.
        if (last != null && now - last in 0 until CHECK_INTERVAL_MILLIS) return null
        return try {
            check().also { preferences.setLastUpdateCheckMillis(now) }
        } catch (_: RemoteException) {
            null
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1_000L
    }
}

/**
 * True if [candidate] is a later version than [installed]. Both are dotted numbers like "0.1.10";
 * missing parts count as 0, and a part counts by its leading digits ("2-beta" is 2).
 */
internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    val a = versionParts(candidate)
    val b = versionParts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    return false
}

private fun versionParts(version: String): List<Int> =
    version.split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
