package com.quantumff.segue.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.quantumff.segue.core.designsystem.theme.SegueAlpha
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlinx.collections.immutable.ImmutableList

/** Section header (DESIGN.md §4.10): `labelLarge` in `primary`, 24 top padding. */
@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SegueSpacing.lg, end = SegueSpacing.lg, top = SegueSpacing.xl, bottom = SegueSpacing.xs)
            .semantics { heading() },
    )
}

/** An M3 [ListItem] on the background: 56 tall with one line, 72 with a summary. */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else SegueAlpha.DISABLED
    ListItem(
        headlineContent = {
            Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        },
        supportingContent = summary?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        },
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.size(SegueSizes.icon),
                )
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier,
    )
}

/** Picker (DESIGN.md §4.10): a bottom sheet of radio rows, never a dialog. Selecting dismisses it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> PickerSheet(
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
        Column(
            Modifier
                .navigationBarsPadding()
                .selectableGroup(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.md),
            )
            options.forEach { option ->
                val isSelected = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SegueSizes.touchTarget)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(option)
                                onDismissRequest()
                            },
                        )
                        .padding(horizontal = SegueSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Spacer(Modifier.width(SegueSpacing.lg))
                    Text(label(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(SegueSpacing.sm))
        }
    }
}
