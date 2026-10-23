package com.quantumff.segue.core.model

/**
 * What a screen, or a tab of one, shows (DESIGN.md §5.8): skeletons while it loads with nothing in
 * Room, its content, an empty state, or an error with Retry when it has no data because loading
 * failed.
 */
sealed interface ScreenState {
    data object Loading : ScreenState

    data object Content : ScreenState

    data object Empty : ScreenState

    data class Error(val cause: Cause) : ScreenState

    /** Why loading failed: the device is Offline, or the server could not be reached or refused. */
    enum class Cause {
        Offline,
        Server,
    }

    companion object {
        /**
         * The state of a screen showing [itemCount] items from Room, null until Room answers.
         * Data always shows. With none, a load still running counts as loading, and a [failure]
         * is an error rather than an empty library.
         */
        fun of(itemCount: Int?, isLoading: Boolean = false, failure: Cause? = null): ScreenState = when {
            itemCount == null -> Loading
            itemCount > 0 -> Content
            isLoading -> Loading
            failure != null -> Error(failure)
            else -> Empty
        }
    }
}
