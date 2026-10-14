package com.quantumff.segue.feature.queue.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.quantumff.segue.feature.queue.QueueScreen
import kotlinx.serialization.Serializable

@Serializable
data object QueueRoute

fun NavController.navigateToQueue(navOptions: NavOptions? = null) = navigate(QueueRoute, navOptions)

fun NavGraphBuilder.queueScreen() {
    composable<QueueRoute> {
        QueueScreen()
    }
}
