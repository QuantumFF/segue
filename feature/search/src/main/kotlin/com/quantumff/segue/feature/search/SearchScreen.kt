package com.quantumff.segue.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.PlaceholderScreen
import com.quantumff.segue.core.designsystem.component.SearchTopBar
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview

/** Placeholder. Real UI follows docs/DESIGN.md §4.6. The query moves to a ViewModel with search. */
@Composable
fun SearchScreen(modifier: Modifier = Modifier, autoFocus: Boolean = true) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        SearchTopBar(query = query, onQueryChange = { query = it }, autoFocus = autoFocus)
        PlaceholderScreen(title = stringResource(R.string.feature_search_placeholder), modifier = Modifier.weight(1f))
    }
}

@PreviewSegueThemes
@Composable
private fun SearchScreenPreview() {
    SeguePreview { SearchScreen(autoFocus = false) }
}
