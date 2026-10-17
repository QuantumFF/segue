package com.quantumff.segue.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project

/**
 * The app version, taken from the release tag instead of being edited by hand.
 *
 * The release workflow passes the tag without its `v` as `-PsegueVersion=1.2.3` (or `1.2.3-beta.2`).
 * Builds without it are local builds and report [DEV].
 */
internal fun Project.configureSegueVersion(extension: ApplicationExtension) {
    val version = providers.gradleProperty("segueVersion").map(SegueVersion::parse).getOrElse(SegueVersion.DEV)
    extension.defaultConfig.versionName = version.name
    extension.defaultConfig.versionCode = version.code
}

internal data class SegueVersion(val name: String, val code: Int) {
    companion object {
        val DEV = SegueVersion("0.0.0-dev", 1)

        private val SEMVER = Regex("""(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)(?:\.(\d+))?)?""")
        private val STAGES = listOf("alpha", "beta", "rc")
        private const val MAX_MAJOR = 2099 // Google Play caps versionCode at 2,100,000,000.

        /**
         * versionCode is major × 1,000,000 + minor × 10,000 + patch × 100 + a two-digit suffix.
         * The suffix is 99 for a final release, and stage then number for a pre-release
         * (alpha 1N, beta 2N, rc 3N), so every pre-release sorts below its final release:
         * 1.2.0-beta.2 is 1020022 and 1.2.0 is 1020099.
         */
        fun parse(name: String): SegueVersion {
            val match = requireNotNull(SEMVER.matchEntire(name)) {
                "Version '$name' must look like 1.2.3, 1.2.3-alpha.N, 1.2.3-beta.N or 1.2.3-rc.N."
            }
            val (major, minor, patch) = match.destructured.toList().take(3).map { it.toInt() }
            val stage = match.groupValues[4]
            val number = match.groupValues[5].ifEmpty { "0" }.toInt()
            require(major <= MAX_MAJOR) { "Version '$name': major must be at most $MAX_MAJOR." }
            require(minor < 100 && patch < 100) { "Version '$name': minor and patch must be below 100." }
            require(number < 10) { "Version '$name': pre-release number must be below 10." }
            val suffix = if (stage.isEmpty()) 99 else (STAGES.indexOf(stage) + 1) * 10 + number
            return SegueVersion(name, major * 1_000_000 + minor * 10_000 + patch * 100 + suffix)
        }
    }
}
