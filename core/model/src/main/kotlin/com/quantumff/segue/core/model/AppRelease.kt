package com.quantumff.segue.core.model

/** A published Segue release. [version] has no "v" prefix; [url] is its GitHub release page. */
data class AppRelease(val version: String, val url: String)
