package com.quantumff.segue.core.datastore

/** App settings. Grows as features land. */
data class UserPreferences(
    /** Adopt a newer server play queue without asking. See PLAN.md, "Server queue sync". */
    val adoptRemoteQueueAutomatically: Boolean = false,
)
