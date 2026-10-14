package com.quantumff.segue.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.quantumff.segue.core.designsystem.R
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.tabularNumbers

/** A newer server queue saved by another client (DESIGN.md §4.8). */
@Immutable
data class ContinueFromDeviceState(
    /** The other client's name, e.g. "Feishin". */
    val deviceName: String,
    /** "Nude · 2:31 · 5 min ago", already formatted. */
    val detail: String,
)

/**
 * "Continue from <device>" card for the top of the queue: radius `lg`,
 * `surfaceContainerHighest`, 16 padding, a `monitor-smartphone` icon, then Continue / Dismiss.
 */
@Composable
fun ContinueFromDeviceBanner(
    state: ContinueFromDeviceState,
    onContinueClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, SegueShapes.lg)
            .padding(SegueSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeviceIcon()
            Spacer(Modifier.width(SegueSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = continueFromText(state.deviceName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.detail,
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(SegueSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SegueSpacing.sm, Alignment.End),
        ) {
            TextButton(onClick = onDismissClick) { Text(stringResource(R.string.ds_dismiss)) }
            FilledTonalButton(onClick = onContinueClick) { Text(stringResource(R.string.ds_continue)) }
        }
    }
}

/**
 * The compact one-line version that docks above the mini player on app start, or replaces it
 * while the local queue is empty.
 */
@Composable
fun ContinueFromDeviceBar(
    state: ContinueFromDeviceState,
    onContinueClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SegueSizes.miniPlayer)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = SegueSpacing.lg, end = SegueSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceIcon()
        Spacer(Modifier.width(SegueSpacing.md))
        Text(
            text = continueFromText(state.deviceName),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismissClick) { Text(stringResource(R.string.ds_dismiss)) }
        FilledTonalButton(onClick = onContinueClick) { Text(stringResource(R.string.ds_continue)) }
    }
}

@Composable
private fun DeviceIcon() {
    Icon(
        imageVector = SegueIcons.OtherDevice,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(SegueSizes.icon),
    )
}

/** "Continue from **Feishin**": the device name in bold. */
@Composable
private fun continueFromText(deviceName: String): AnnotatedString {
    val full = stringResource(R.string.ds_continue_from, deviceName)
    val start = full.lastIndexOf(deviceName)
    return buildAnnotatedString {
        append(full)
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + deviceName.length)
    }
}

private val PreviewState = ContinueFromDeviceState(deviceName = "Feishin", detail = "Nude · 2:31 · 5 min ago")

@PreviewSegueThemes
@Composable
private fun ContinueFromDeviceBannerPreview() {
    SeguePreview {
        Column(Modifier.padding(SegueSpacing.lg), verticalArrangement = Arrangement.spacedBy(SegueSpacing.lg)) {
            ContinueFromDeviceBanner(state = PreviewState, onContinueClick = {}, onDismissClick = {})
            ContinueFromDeviceBar(state = PreviewState, onContinueClick = {}, onDismissClick = {})
        }
    }
}
