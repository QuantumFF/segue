package com.quantumff.segue.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("No library '$alias' in libs.versions.toml") }

internal fun VersionCatalog.versionInt(alias: String): Int =
    findVersion(alias).orElseThrow { IllegalArgumentException("No version '$alias' in libs.versions.toml") }
        .requiredVersion
        .toInt()

/** `:core:model` becomes `com.quantumff.segue.core.model`. */
internal val Project.segueNamespace: String
    get() = "com.quantumff.segue" + path.replace(':', '.').replace('-', '_')
