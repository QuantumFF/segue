package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A picker (DESIGN.md §4.10, §5.7): a bottom sheet with a heading and 48-tall radio rows, never a
 * dialog. Selecting an option reports it and dismisses the sheet. Long lists scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerSheet(
    title: String,
    options: ImmutableList<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SegueShapes.sheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = SegueSizes.noElevation,
        modifier = modifier,
    ) {
        PickerSheetContent(
            title = title,
            options = options,
            selected = selected,
            label = label,
            onSelect = {
                onSelect(it)
                onDismissRequest()
            },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** The body of a [PickerSheet]: its heading and radio rows, without the sheet around them. */
@Composable
fun <T> PickerSheetContent(
    title: String,
    options: ImmutableList<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.md)
                .semantics { heading() },
        )
        LazyColumn(Modifier.selectableGroup()) {
            items(options) { option ->
                PickerRow(
                    label = label(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
        Spacer(Modifier.height(SegueSpacing.sm))
    }
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegueSizes.sheetRow)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = SegueSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(SegueSpacing.lg))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@PreviewSegueThemes
@Composable
private fun PickerSheetPreview() {
    SeguePreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            val options = persistentListOf("Recently added", "Name", "Artist", "Year")
            PickerSheetContent(
                title = "Sort",
                options = options,
                selected = options.first(),
                label = { it },
                onSelect = {},
            )
        }
    }
}
