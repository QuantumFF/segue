package com.quantumff.segue.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.AccentPreset as AccentPresetColors
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.designsystem.util.openLink
import com.quantumff.segue.core.model.AccentPreset
import com.quantumff.segue.core.model.AccentSource
import com.quantumff.segue.core.model.DownloadPreferences
import com.quantumff.segue.core.model.ReplayGainMode
import com.quantumff.segue.core.model.ReplayGainTarget
import com.quantumff.segue.core.model.Server
import com.quantumff.segue.core.model.StorageLimit
import com.quantumff.segue.core.model.StreamingQuality
import com.quantumff.segue.core.model.ThemeMode
import com.quantumff.segue.core.model.UserPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Settings (DESIGN.md §4.10). Stateful wrapper. */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onAddServerClick: () -> Unit,
    onLicensesClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel, uriHandler) {
        viewModel.openRelease.collect { url -> uriHandler.openLink(url) }
    }
    val context = LocalContext.current
    val playOnBluetoothConnect = { on: Boolean ->
        viewModel.updatePreferences { it.copy(playOnBluetoothConnect = on) }
    }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) playOnBluetoothConnect(true)
    }
    // Without the permission no connection is ever heard, so a revoked permission turns the switch off.
    val playsOnBluetoothConnect = uiState.preferences.playOnBluetoothConnect
    LifecycleResumeEffect(playsOnBluetoothConnect) {
        if (playsOnBluetoothConnect && !hasBluetoothConnectPermission(context)) playOnBluetoothConnect(false)
        onPauseOrDispose {}
    }
    val actions = remember(viewModel, onDownloadsClick, onAddServerClick, onLicensesClick, bluetoothPermission) {
        SettingsActions(
            onSwitchServer = viewModel::switchServer,
            onRemoveServer = viewModel::removeServer,
            onAddServer = onAddServerClick,
            onThemeMode = { mode -> viewModel.updatePreferences { it.copy(themeMode = mode) } },
            onAccentSource = { source -> viewModel.updatePreferences { it.copy(accentSource = source) } },
            onAccentPreset = { preset -> viewModel.updatePreferences { it.copy(accentPreset = preset) } },
            onReplayGainMode = { mode -> viewModel.updatePreferences { it.copy(replayGainMode = mode) } },
            onReplayGainTarget = { target -> viewModel.updatePreferences { it.copy(replayGainTarget = target) } },
            onReplayGainBoost = { on -> viewModel.updatePreferences { it.copy(replayGainBoost = on) } },
            onWifiQuality = { quality -> viewModel.updatePreferences { it.copy(wifiStreamingQuality = quality) } },
            onCellularQuality = { quality ->
                viewModel.updatePreferences { it.copy(cellularStreamingQuality = quality) }
            },
            onStreamCacheSize = { mb -> viewModel.updatePreferences { it.copy(streamCacheSizeMb = mb) } },
            onStopWhenPaused = { minutes -> viewModel.updatePreferences { it.copy(stopWhenPausedMinutes = minutes) } },
            onPlayOnBluetoothConnect = { on ->
                if (on && !hasBluetoothConnectPermission(context)) {
                    bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    playOnBluetoothConnect(on)
                }
            },
            onDownloads = onDownloadsClick,
            onQueueSync = { on -> viewModel.updatePreferences { it.copy(queueSyncEnabled = on) } },
            onAdoptRemoteQueue = { on -> viewModel.updatePreferences { it.copy(adoptRemoteQueueAutomatically = on) } },
            onLicenses = onLicensesClick,
            onDownloadQuality = viewModel::setDownloadQuality,
            onDownloadWifiOnly = viewModel::setDownloadWifiOnly,
            onDownloadChargingOnly = viewModel::setDownloadChargingOnly,
            onOfflineMode = viewModel::setOfflineMode,
            onStorageLimit = viewModel::setStorageLimit,
            onCheckForUpdates = viewModel::checkForUpdates,
            onAutoUpdateCheck = { on -> viewModel.updatePreferences { it.copy(checkForUpdates = on) } },
        )
    }
    SettingsContent(
        uiState = uiState,
        versionName = viewModel.versionName,
        actions = actions,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

/** Every event the settings screen raises. */
class SettingsActions(
    val onSwitchServer: (String) -> Unit,
    val onRemoveServer: (String) -> Unit,
    val onAddServer: () -> Unit,
    val onThemeMode: (ThemeMode) -> Unit,
    val onAccentSource: (AccentSource) -> Unit,
    val onAccentPreset: (AccentPreset) -> Unit,
    val onReplayGainMode: (ReplayGainMode) -> Unit,
    val onReplayGainTarget: (ReplayGainTarget) -> Unit,
    val onReplayGainBoost: (Boolean) -> Unit,
    val onWifiQuality: (StreamingQuality) -> Unit,
    val onCellularQuality: (StreamingQuality) -> Unit,
    val onStreamCacheSize: (Int) -> Unit,
    val onStopWhenPaused: (Int) -> Unit,
    val onDownloads: () -> Unit,
    val onQueueSync: (Boolean) -> Unit,
    val onAdoptRemoteQueue: (Boolean) -> Unit,
    val onLicenses: () -> Unit,
    val onDownloadQuality: (StreamingQuality) -> Unit = {},
    val onDownloadWifiOnly: (Boolean) -> Unit = {},
    val onDownloadChargingOnly: (Boolean) -> Unit = {},
    val onOfflineMode: (Boolean) -> Unit = {},
    val onStorageLimit: (StorageLimit) -> Unit = {},
    val onCheckForUpdates: () -> Unit = {},
    val onAutoUpdateCheck: (Boolean) -> Unit = {},
    val onPlayOnBluetoothConnect: (Boolean) -> Unit = {},
) {
    companion object {
        val None = SettingsActions(
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

/** Which picker sheet is open. */
private enum class SettingsPicker {
    Theme,
    AccentSource,
    ReplayGain,
    ReplayGainTarget,
    WifiQuality,
    CellularQuality,
    CacheSize,
    StopWhenPaused,
    DownloadQuality,
    StorageLimit,
}

/** The stateless settings screen. */
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    versionName: String,
    actions: SettingsActions,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }
    var removing by remember { mutableStateOf<Server?>(null) }
    val prefs = uiState.preferences
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()

    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = stringResource(R.string.feature_settings_title), onBackClick = onBackClick)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + SegueSpacing.lg)) {
            item(key = "servers", contentType = "header") {
                SettingsSectionHeader(stringResource(R.string.feature_settings_servers))
            }
            items(uiState.servers, key = { "server:${it.id}" }, contentType = { "server" }) { server ->
                ServerRow(
                    server = server,
                    active = server.id == uiState.activeServerId,
                    onClick = { actions.onSwitchServer(server.id) },
                    onRemoveClick = { removing = server },
                )
            }
            item(key = "addServer", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_add_server),
                    icon = SegueIcons.Plus,
                    modifier = Modifier.clickable(onClick = actions.onAddServer),
                )
            }

            item(key = "appearance", contentType = "header") {
                SettingsSectionHeader(stringResource(R.string.feature_settings_appearance))
            }
            item(key = "theme", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_theme),
                    summary = themeLabel(prefs.themeMode),
                    modifier = Modifier.clickable { picker = SettingsPicker.Theme },
                )
            }
            item(key = "accentSource", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_accent_source),
                    summary = accentSourceLabel(prefs.accentSource),
                    modifier = Modifier.clickable { picker = SettingsPicker.AccentSource },
                )
            }
            if (prefs.accentSource == AccentSource.Fixed) {
                item(key = "accentPreset", contentType = "swatches") {
                    AccentSwatches(selected = prefs.accentPreset, onSelect = actions.onAccentPreset)
                }
            }

            item(key = "playback", contentType = "header") {
                SettingsSectionHeader(stringResource(R.string.feature_settings_playback))
            }
            item(key = "replayGain", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_replay_gain),
                    summary = replayGainLabel(prefs.replayGainMode),
                    modifier = Modifier.clickable { picker = SettingsPicker.ReplayGain },
                )
            }
            item(key = "replayGainTarget", contentType = "row") {
                val enabled = prefs.replayGainMode != ReplayGainMode.Off
                SettingsRow(
                    title = stringResource(R.string.feature_settings_replay_gain_target),
                    summary = stringResource(
                        R.string.feature_settings_replay_gain_target_summary,
                        replayGainTargetLabel(prefs.replayGainTarget),
                    ),
                    enabled = enabled,
                    modifier = Modifier.clickable(enabled = enabled) { picker = SettingsPicker.ReplayGainTarget },
                )
            }
            item(key = "replayGainBoost", contentType = "switch") {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_replay_gain_boost),
                    summary = stringResource(R.string.feature_settings_replay_gain_boost_summary),
                    checked = prefs.replayGainBoost,
                    enabled = prefs.replayGainMode != ReplayGainMode.Off,
                    onCheckedChange = actions.onReplayGainBoost,
                )
            }
            item(key = "stopWhenPaused", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_stop_when_paused),
                    summary = minutesLabel(prefs.stopWhenPausedMinutes),
                    modifier = Modifier.clickable { picker = SettingsPicker.StopWhenPaused },
                )
            }
            item(key = "playOnBluetoothConnect", contentType = "switch") {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_play_on_bluetooth_connect),
                    summary = stringResource(R.string.feature_settings_play_on_bluetooth_connect_summary),
                    checked = prefs.playOnBluetoothConnect,
                    onCheckedChange = actions.onPlayOnBluetoothConnect,
                )
            }

            item(key = "streaming", contentType = "header") {
                SettingsSectionHeader(stringResource(R.string.feature_settings_streaming))
            }
            item(key = "wifi", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_quality_wifi),
                    summary = qualityLabel(prefs.wifiStreamingQuality),
                    modifier = Modifier.clickable { picker = SettingsPicker.WifiQuality },
                )
            }
            item(key = "cellular", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_quality_cellular),
                    summary = qualityLabel(prefs.cellularStreamingQuality),
                    modifier = Modifier.clickable { picker = SettingsPicker.CellularQuality },
                )
            }

            item(key = "storage", contentType = "header") {
                SettingsSectionHeader(stringResource(R.string.feature_settings_storage))
            }
            item(key = "cache", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_cache_size),
                    summary = sizeLabel(prefs.streamCacheSizeMb),
                    modifier = Modifier.clickable { picker = SettingsPicker.CacheSize },
                )
            }
            item(key = "downloadQuality", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_download_quality),
                    summary = qualityLabel(uiState.downloads.quality),
                    modifier = Modifier.clickable { picker = SettingsPicker.DownloadQuality },
                )
            }
            item(key = "storageLimit", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_storage_limit),
                    summary = storageLimitLabel(uiState.downloads.storageLimit),
                    modifier = Modifier.clickable { picker = SettingsPicker.StorageLimit },
                )
            }
            item(key = "downloadWifiOnly", contentType = "switch") {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_download_wifi_only),
                    summary = stringResource(R.string.feature_settings_download_wifi_only_summary),
                    checked = uiState.downloads.wifiOnly,
                    onCheckedChange = actions.onDownloadWifiOnly,
                )
            }
            item(key = "downloadCharging", contentType = "switch") {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_download_charging_only),
                    summary = stringResource(R.string.feature_settings_download_charging_only_summary),
                    checked = uiState.downloads.chargingOnly,
                    onCheckedChange = actions.onDownloadChargingOnly,
                )
            }
            item(key = "offlineMode", contentType = "switch") {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_offline_mode),
                    summary = stringResource(R.string.feature_settings_offline_mode_summary),
                    checked = uiState.downloads.forceOffline,
                    onCheckedChange = actions.onOfflineMode,
                )
            }
            item(key = "downloads", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_downloads),
                    icon = SegueIcons.Download,
                    modifier = Modifier.clickable(onClick = actions.onDownloads),
                )
            }

            item(key = "queueSync", contentType = "header") {
                SettingsSectionHeader(stringResource(R.string.feature_settings_queue_sync))
            }
            item(key = "queueSyncEnabled", contentType = "switch") {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_queue_sync_enabled),
                    summary = stringResource(R.string.feature_settings_queue_sync_enabled_summary),
                    checked = prefs.queueSyncEnabled,
                    onCheckedChange = actions.onQueueSync,
                )
            }
            item(key = "adoptQueue", contentType = "switch") {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_adopt_queue),
                    summary = stringResource(R.string.feature_settings_adopt_queue_summary),
                    checked = prefs.adoptRemoteQueueAutomatically,
                    enabled = prefs.queueSyncEnabled,
                    onCheckedChange = actions.onAdoptRemoteQueue,
                )
            }

            item(key = "about", contentType = "header") {
                SettingsSectionHeader(stringResource(R.string.feature_settings_about))
            }
            item(key = "version", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_version),
                    summary = versionName,
                    icon = SegueIcons.Info,
                )
            }
            item(key = "checkForUpdates", contentType = "row") {
                val checking = uiState.updateCheck == UpdateCheck.Checking
                SettingsRow(
                    title = stringResource(R.string.feature_settings_check_for_updates),
                    summary = updateCheckLabel(uiState.updateCheck),
                    modifier = Modifier.clickable(enabled = !checking, onClick = actions.onCheckForUpdates),
                )
            }
            item(key = "autoUpdateCheck", contentType = "switch") {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_auto_update_check),
                    summary = stringResource(R.string.feature_settings_auto_update_check_summary),
                    checked = prefs.checkForUpdates,
                    onCheckedChange = actions.onAutoUpdateCheck,
                )
            }
            item(key = "licenses", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.feature_settings_licenses),
                    summary = stringResource(R.string.feature_settings_licenses_summary),
                    modifier = Modifier.clickable(onClick = actions.onLicenses),
                )
            }
        }
    }

    SettingsPickerSheet(
        picker = picker,
        prefs = prefs,
        downloads = uiState.downloads,
        actions = actions,
        onDismissRequest = { picker = null },
    )

    removing?.let { server ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.feature_settings_remove_server_title, server.name)) },
            text = { Text(stringResource(R.string.feature_settings_remove_server_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onRemoveServer(server.id)
                        removing = null
                    },
                ) {
                    Text(
                        stringResource(R.string.feature_settings_remove_server_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.feature_settings_cancel)) }
            },
            shape = SegueShapes.xl,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = SegueSizes.noElevation,
        )
    }
}

@Composable
private fun SettingsPickerSheet(
    picker: SettingsPicker?,
    prefs: UserPreferences,
    downloads: DownloadPreferences,
    actions: SettingsActions,
    onDismissRequest: () -> Unit,
) {
    when (picker) {
        null -> Unit

        SettingsPicker.Theme -> PickerSheet(
            title = stringResource(R.string.feature_settings_theme),
            options = ThemeMode.entries.toImmutableList(),
            selected = prefs.themeMode,
            label = { themeLabel(it) },
            onSelect = actions.onThemeMode,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.AccentSource -> PickerSheet(
            title = stringResource(R.string.feature_settings_accent_source),
            options = AccentSource.entries.toImmutableList(),
            selected = prefs.accentSource,
            label = { accentSourceLabel(it) },
            onSelect = actions.onAccentSource,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.ReplayGain -> PickerSheet(
            title = stringResource(R.string.feature_settings_replay_gain),
            options = ReplayGainMode.entries.toImmutableList(),
            selected = prefs.replayGainMode,
            label = { replayGainLabel(it) },
            onSelect = actions.onReplayGainMode,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.ReplayGainTarget -> PickerSheet(
            title = stringResource(R.string.feature_settings_replay_gain_target),
            options = ReplayGainTarget.entries.toImmutableList(),
            selected = prefs.replayGainTarget,
            label = { replayGainTargetLabel(it) },
            onSelect = actions.onReplayGainTarget,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.WifiQuality -> PickerSheet(
            title = stringResource(R.string.feature_settings_quality_wifi),
            options = SettingsViewModel.STREAMING_QUALITIES,
            selected = prefs.wifiStreamingQuality,
            label = { qualityLabel(it) },
            onSelect = actions.onWifiQuality,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.CellularQuality -> PickerSheet(
            title = stringResource(R.string.feature_settings_quality_cellular),
            options = SettingsViewModel.STREAMING_QUALITIES,
            selected = prefs.cellularStreamingQuality,
            label = { qualityLabel(it) },
            onSelect = actions.onCellularQuality,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.DownloadQuality -> PickerSheet(
            title = stringResource(R.string.feature_settings_download_quality),
            options = SettingsViewModel.STREAMING_QUALITIES,
            selected = downloads.quality,
            label = { qualityLabel(it) },
            onSelect = actions.onDownloadQuality,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.StorageLimit -> PickerSheet(
            title = stringResource(R.string.feature_settings_storage_limit),
            options = StorageLimit.entries.toImmutableList(),
            selected = downloads.storageLimit,
            label = { storageLimitLabel(it) },
            onSelect = actions.onStorageLimit,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.CacheSize -> PickerSheet(
            title = stringResource(R.string.feature_settings_cache_size),
            options = UserPreferences.STREAM_CACHE_SIZES_MB.toImmutableList(),
            selected = prefs.streamCacheSizeMb,
            label = { sizeLabel(it) },
            onSelect = actions.onStreamCacheSize,
            onDismissRequest = onDismissRequest,
        )

        SettingsPicker.StopWhenPaused -> PickerSheet(
            title = stringResource(R.string.feature_settings_stop_when_paused),
            options = UserPreferences.STOP_WHEN_PAUSED_MINUTES.toImmutableList(),
            selected = prefs.stopWhenPausedMinutes,
            label = { minutesLabel(it) },
            onSelect = actions.onStopWhenPaused,
            onDismissRequest = onDismissRequest,
        )
    }
}

/** "No limit" or "10 GB". */
@Composable
private fun storageLimitLabel(limit: StorageLimit): String = limit.gigabytes
    ?.let { stringResource(R.string.feature_settings_storage_limit_gb, it) }
    ?: stringResource(R.string.feature_settings_storage_limit_none)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerRow(server: Server, active: Boolean, onClick: () -> Unit, onRemoveClick: () -> Unit) {
    SettingsRow(
        title = server.name,
        summary = if (active) {
            stringResource(R.string.feature_settings_server_active, server.username, server.baseUrl)
        } else {
            stringResource(R.string.feature_settings_server_summary, server.username, server.baseUrl)
        },
        icon = SegueIcons.Server,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onRemoveClick),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    Icon(
                        imageVector = SegueIcons.Check,
                        contentDescription = stringResource(R.string.feature_settings_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(SegueSizes.iconSmall),
                    )
                }
                SegueIconButton(
                    icon = SegueIcons.Remove,
                    contentDescription = stringResource(R.string.feature_settings_remove_server),
                    onClick = onRemoveClick,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun AccentSwatches(selected: AccentPreset, onSelect: (AccentPreset) -> Unit) {
    val dark = SegueTheme.isDark
    SettingsRow(title = stringResource(R.string.feature_settings_accent_color))
    Row(
        horizontalArrangement = Arrangement.spacedBy(SegueSpacing.xs),
        modifier = Modifier.padding(start = SegueSpacing.sm, end = SegueSpacing.lg, bottom = SegueSpacing.sm),
    ) {
        AccentPreset.entries.forEach { preset ->
            val colors = AccentPresetColors.valueOf(preset.name)
            val color = if (dark) colors.dark else colors.light
            val isSelected = preset == selected
            val name = accentPresetLabel(preset)
            Box(
                modifier = Modifier
                    .size(SegueSizes.touchTarget)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(preset) })
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(SWATCH)
                        .background(color, SegueShapes.full)
                        .then(
                            if (isSelected) {
                                Modifier.border(SWATCH_RING, MaterialTheme.colorScheme.onSurface, SegueShapes.full)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = SegueIcons.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(SegueSizes.iconSmall),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        summary = summary,
        enabled = enabled,
        modifier = Modifier.clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.System -> R.string.feature_settings_theme_system
        ThemeMode.Light -> R.string.feature_settings_theme_light
        ThemeMode.Dark -> R.string.feature_settings_theme_dark
        ThemeMode.Black -> R.string.feature_settings_theme_black
    },
)

@Composable
private fun accentSourceLabel(source: AccentSource): String = stringResource(
    when (source) {
        AccentSource.Fixed -> R.string.feature_settings_accent_fixed
        AccentSource.MaterialYou -> R.string.feature_settings_accent_material_you
        AccentSource.AlbumArt -> R.string.feature_settings_accent_album_art
    },
)

@Composable
private fun accentPresetLabel(preset: AccentPreset): String = stringResource(
    when (preset) {
        AccentPreset.Blue -> R.string.feature_settings_accent_blue
        AccentPreset.Teal -> R.string.feature_settings_accent_teal
        AccentPreset.Violet -> R.string.feature_settings_accent_violet
        AccentPreset.Pink -> R.string.feature_settings_accent_pink
        AccentPreset.Orange -> R.string.feature_settings_accent_orange
        AccentPreset.Green -> R.string.feature_settings_accent_green
    },
)

@Composable
private fun replayGainLabel(mode: ReplayGainMode): String = stringResource(
    when (mode) {
        ReplayGainMode.Off -> R.string.feature_settings_replay_gain_off
        ReplayGainMode.Track -> R.string.feature_settings_replay_gain_track
        ReplayGainMode.Album -> R.string.feature_settings_replay_gain_album
    },
)

@Composable
private fun replayGainTargetLabel(target: ReplayGainTarget): String = stringResource(
    when (target) {
        ReplayGainTarget.ReplayGain2 -> R.string.feature_settings_replay_gain_target_rg2
        ReplayGainTarget.Ebu -> R.string.feature_settings_replay_gain_target_ebu
    },
)

@Composable
private fun qualityLabel(quality: StreamingQuality): String {
    val format = quality.format
    return when {
        quality.maxBitRateKbps == 0 && format == null -> stringResource(R.string.feature_settings_quality_original)

        format == null -> stringResource(R.string.feature_settings_quality_bitrate, quality.maxBitRateKbps)

        else -> stringResource(
            R.string.feature_settings_quality_format,
            format.uppercase(),
            quality.maxBitRateKbps,
        )
    }
}

@Composable
private fun sizeLabel(megabytes: Int): String = if (megabytes >= MB_PER_GB && megabytes % MB_PER_GB == 0) {
    stringResource(R.string.feature_settings_size_gb, megabytes / MB_PER_GB)
} else {
    stringResource(R.string.feature_settings_size_mb, megabytes)
}

@Composable
private fun updateCheckLabel(check: UpdateCheck): String = when (check) {
    UpdateCheck.Idle -> stringResource(R.string.feature_settings_check_for_updates_summary)
    UpdateCheck.Checking -> stringResource(R.string.feature_settings_update_checking)
    UpdateCheck.UpToDate -> stringResource(R.string.feature_settings_update_up_to_date)
    is UpdateCheck.Available -> stringResource(R.string.feature_settings_update_available, check.version)
    UpdateCheck.Failed -> stringResource(R.string.feature_settings_update_failed)
}

@Composable
private fun minutesLabel(minutes: Int): String =
    pluralStringResource(R.plurals.feature_settings_minutes, minutes, minutes)

private const val MB_PER_GB = 1024
private val SWATCH = 32.dp
private val SWATCH_RING = 2.dp

@PreviewSegueThemes
@Composable
private fun SettingsScreenPreview() {
    SeguePreview {
        SettingsContent(
            uiState = SettingsUiState(
                servers = persistentListOf(
                    Server("1", "demo.navidrome.org", "https://demo.navidrome.org", "demo"),
                    Server("2", "Home", "https://music.example.com", "me"),
                ),
                activeServerId = "1",
            ),
            versionName = "1.0.0",
            actions = SettingsActions.None,
            onBackClick = {},
        )
    }
}
