package com.quantumff.segue.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.quantumff.segue.navigation.SegueNavHost

/** App shell. Navigation chrome (bars, mini player) comes from docs/DESIGN.md. */
@Composable
fun SegueApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    Scaffold(modifier = modifier) { innerPadding ->
        SegueNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
