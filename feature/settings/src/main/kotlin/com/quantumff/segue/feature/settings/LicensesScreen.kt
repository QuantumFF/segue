package com.quantumff.segue.feature.settings

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import com.quantumff.segue.core.designsystem.component.LocalShellContentPadding
import com.quantumff.segue.core.designsystem.component.SubScreenTopBar
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One component and its license. The text is a raw resource ([textRes]) or a string ([summaryRes]). */
@Immutable
internal data class LicenseEntry(
    val name: String,
    @param:StringRes val licenseName: Int,
    @param:RawRes val textRes: Int? = null,
    @param:StringRes val summaryRes: Int? = null,
)

private val APACHE = R.string.feature_settings_license_apache_text

internal val Licenses: ImmutableList<LicenseEntry> = persistentListOf(
    LicenseEntry(
        "Segue",
        R.string.feature_settings_license_gpl,
        summaryRes = R.string.feature_settings_license_gpl_text,
    ),
    LicenseEntry("Inter", R.string.feature_settings_license_ofl, textRes = R.raw.inter_ofl),
    LicenseEntry("Lucide", R.string.feature_settings_license_isc, textRes = R.raw.lucide_isc),
    LicenseEntry("AndroidX and Jetpack Compose", R.string.feature_settings_license_apache, summaryRes = APACHE),
    LicenseEntry("Media3 (ExoPlayer)", R.string.feature_settings_license_apache, summaryRes = APACHE),
    LicenseEntry("Room, Paging, WorkManager, DataStore", R.string.feature_settings_license_apache, summaryRes = APACHE),
    LicenseEntry("Dagger and Hilt", R.string.feature_settings_license_apache, summaryRes = APACHE),
    LicenseEntry("OkHttp and Retrofit", R.string.feature_settings_license_apache, summaryRes = APACHE),
    LicenseEntry("Kotlin and kotlinx libraries", R.string.feature_settings_license_apache, summaryRes = APACHE),
    LicenseEntry("Coil", R.string.feature_settings_license_apache, summaryRes = APACHE),
    LicenseEntry("Reorderable", R.string.feature_settings_license_apache, summaryRes = APACHE),
)

/** About › Open-source licenses: tap an entry to show its license text. */
@Composable
fun LicensesScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val bottom = LocalShellContentPadding.current.calculateBottomPadding()
    Column(modifier.fillMaxSize()) {
        SubScreenTopBar(title = stringResource(R.string.feature_settings_licenses_title), onBackClick = onBackClick)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + SegueSpacing.lg)) {
            items(Licenses, key = { it.name }, contentType = { "license" }) { entry ->
                val isExpanded = expanded == entry.name
                Column {
                    SettingsRow(
                        title = entry.name,
                        summary = stringResource(entry.licenseName),
                        modifier = Modifier.clickable { expanded = if (isExpanded) null else entry.name },
                        trailing = {
                            Icon(
                                imageVector = if (isExpanded) SegueIcons.ChevronDown else SegueIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    if (isExpanded) LicenseText(entry)
                }
            }
        }
    }
}

@Composable
private fun LicenseText(entry: LicenseEntry) {
    val text = when {
        entry.summaryRes != null -> stringResource(entry.summaryRes)
        entry.textRes != null -> rawText(entry.textRes)
        else -> ""
    }
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SegueSpacing.lg, vertical = SegueSpacing.sm),
        )
    }
}

/** Reads a raw text resource off the main thread. */
@Composable
private fun rawText(@RawRes res: Int): String {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val loading = stringResource(R.string.feature_settings_license_loading)
    val text by produceState(loading, res) {
        if (inspection) return@produceState
        value = withContext(Dispatchers.IO) {
            context.resources.openRawResource(res).bufferedReader().use { it.readText() }
        }
    }
    return text
}

@PreviewSegueThemes
@Composable
private fun LicensesScreenPreview() {
    SeguePreview { LicensesScreen(onBackClick = {}) }
}
