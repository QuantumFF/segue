package com.quantumff.segue.core.common

import javax.inject.Qualifier

/** Inject a [kotlinx.coroutines.CoroutineDispatcher] with `@Dispatcher(SegueDispatchers.IO)`. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: SegueDispatchers)

enum class SegueDispatchers {
    Default,
    IO,
}

/** An app-lifetime scope for work that must outlive a screen, such as a debounced queue upload. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
