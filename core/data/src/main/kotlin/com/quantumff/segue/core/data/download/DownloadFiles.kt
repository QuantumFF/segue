package com.quantumff.segue.core.data.download

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where downloaded songs live: `files/downloads/<server>/<song>.<ext>`, in app storage that the
 * system never clears and apart from the stream cache's LRU, so downloads are never evicted.
 * A download in progress writes `<song>.<ext>.part` and is renamed when complete.
 */
@Singleton
internal class DownloadFiles private constructor(rootDirectory: () -> File) {
    constructor(root: File) : this({ root })

    /** Resolves `filesDir` on first use: it is built during `Application.onCreate`, on the main thread. */
    @Inject
    constructor(@ApplicationContext context: Context) : this({ File(context.filesDir, DIRECTORY) })

    val root: File by lazy(rootDirectory)

    fun serverDirectory(serverId: String): File = File(root, safeName(serverId))

    fun fileFor(serverId: String, songId: String, extension: String): File =
        File(serverDirectory(serverId), "${safeName(songId)}.${safeName(extension)}")

    fun partFileFor(target: File): File = File(target.parentFile, target.name + PART_SUFFIX)

    /** Deletes a song's finished and partial files. */
    fun delete(path: String?) {
        if (path == null) return
        val file = File(path)
        file.delete()
        partFileFor(file).delete()
    }

    /** Deletes every partial or finished file of [songId] on [serverId], whatever its extension. */
    fun deleteSong(serverId: String, songId: String) {
        val prefix = safeName(songId) + "."
        serverDirectory(serverId).listFiles { file -> file.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    /** Removes the folders of servers that are no longer signed in. */
    fun deleteServersExcept(serverIds: Set<String>) {
        val keep = serverIds.mapTo(HashSet(), ::safeName)
        root.listFiles()?.filter { it.isDirectory && it.name !in keep }?.forEach { it.deleteRecursively() }
    }

    fun freeBytes(): Long {
        root.mkdirs()
        return StatFs(root.path).availableBytes
    }

    private fun safeName(value: String): String = value.replace(UNSAFE, "_")

    companion object {
        const val DIRECTORY = "downloads"
        const val PART_SUFFIX = ".part"
        private val UNSAFE = Regex("[^A-Za-z0-9._-]")
    }
}
