package top.valency.snapstamp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import top.valency.snapstamp.R
import top.valency.snapstamp.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SettingsStore(context) }
    val settings by store.settingsFlow.collectAsState(initial = top.valency.snapstamp.data.AppSettings())
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionCard(
                    title = stringResource(R.string.settings_group_capture),
                    items = listOf(
                        stringResource(R.string.settings_capture_desc_1),
                        stringResource(R.string.settings_capture_desc_2),
                        stringResource(R.string.settings_capture_desc_3)
                    )
                ) {
                    SliderSetting(
                        stringResource(R.string.settings_default_zoom),
                        settings.defaultZoom,
                        { scope.launch { store.setDefaultZoom(it) } },
                        stringResource(R.string.settings_current_percent, (settings.defaultZoom * 100).toInt())
                    )
                    SliderSetting(
                        stringResource(R.string.settings_default_exposure),
                        settings.defaultExposure.toFloat(),
                        { scope.launch { store.setDefaultExposure(it.toInt()) } },
                        stringResource(R.string.settings_current_int, settings.defaultExposure),
                        -5f..5f,
                        steps = 9
                    )
                    SliderSetting(
                        stringResource(R.string.settings_focus_reset),
                        settings.focusAutoResetSec.toFloat(),
                        { scope.launch { store.setFocusAutoResetSec(it.toInt()) } },
                        stringResource(R.string.settings_current_seconds, settings.focusAutoResetSec),
                        3f..20f,
                        steps = 16
                    )
                    SwitchSetting(stringResource(R.string.settings_shutter_feedback), settings.shutterFeedback) { scope.launch { store.setShutterFeedback(it) } }
                    SwitchSetting(stringResource(R.string.settings_framing_guide), settings.framingGuide) { scope.launch { store.setFramingGuide(it) } }
                    SwitchSetting(stringResource(R.string.settings_drop_animation), settings.dropAnimation) { scope.launch { store.setDropAnimation(it) } }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_group_stamp),
                    items = listOf(
                        stringResource(R.string.settings_stamp_desc_1),
                        stringResource(R.string.settings_stamp_desc_2),
                        stringResource(R.string.settings_stamp_desc_3)
                    )
                ) {
                    SwitchSetting(stringResource(R.string.settings_default_export_with_border), settings.exportWithBorderDefault) { scope.launch { store.setExportWithBorderDefault(it) } }
                    SliderSetting(
                        stringResource(R.string.settings_border_strength),
                        settings.borderThickness,
                        { scope.launch { store.setBorderThickness(it) } },
                        stringResource(R.string.settings_current_percent, (settings.borderThickness * 100).toInt())
                    )
                    SwitchSetting(stringResource(R.string.settings_border_classic), settings.borderClassicStyle) { scope.launch { store.setBorderClassicStyle(it) } }
                    SwitchSetting(stringResource(R.string.settings_write_location), settings.writeLocationExif) { scope.launch { store.setWriteLocationExif(it) } }
                    SwitchSetting(stringResource(R.string.settings_write_remark), settings.writeRemarkExif) { scope.launch { store.setWriteRemarkExif(it) } }
                    //SwitchSetting(stringResource(R.string.settings_info_overlay), settings.infoVisibleOverlay) { scope.launch { store.setInfoVisibleOverlay(it) } }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_group_filter),
                    items = listOf(
                        stringResource(R.string.settings_filter_desc_1),
                        stringResource(R.string.settings_filter_desc_2)
                    )
                ) {
                    SwitchSetting(stringResource(R.string.settings_preview_oil_default), settings.previewOilAsDefault) { scope.launch { store.setPreviewOilAsDefault(it) } }
                    SliderSetting(
                        stringResource(R.string.settings_oil_strength),
                        settings.oilFilterStrength,
                        { scope.launch { store.setOilFilterStrength(it) } },
                        stringResource(R.string.settings_current_percent, (settings.oilFilterStrength * 100).toInt())
                    )
                    SwitchSetting(stringResource(R.string.settings_filter_cache), settings.filterCacheEnabled) { scope.launch { store.setFilterCacheEnabled(it) } }
                    SwitchSetting(stringResource(R.string.settings_large_image_warn), settings.largeImageWarn) { scope.launch { store.setLargeImageWarn(it) } }
                    SwitchSetting(stringResource(R.string.settings_fast_preview_gesture), settings.previewGestureFast) { scope.launch { store.setPreviewGestureFast(it) } }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_group_storage),
                    items = listOf(
                        stringResource(R.string.settings_storage_desc_1),
                        stringResource(R.string.settings_storage_desc_2)
                    )
                ) {
                    SliderSetting(
                        stringResource(R.string.settings_jpeg_quality),
                        settings.jpegQuality.toFloat(),
                        { scope.launch { store.setJpegQuality(it.toInt()) } },
                        stringResource(R.string.settings_current_int, settings.jpegQuality),
                        70f..100f,
                        steps = 29
                    )
                    SwitchSetting(stringResource(R.string.settings_auto_save_after_shot), settings.autoSaveAfterShot) { scope.launch { store.setAutoSaveAfterShot(it) } }
                    SwitchSetting(stringResource(R.string.settings_save_to_album), settings.saveToSystemAlbum) { scope.launch { store.setSaveToSystemAlbum(it) } }
                    SwitchSetting(stringResource(R.string.settings_keep_internal_copy), settings.keepInternalCopy) { scope.launch { store.setKeepInternalCopy(it) } }
                    SwitchSetting(stringResource(R.string.settings_auto_clean_filter_cache), settings.autoCleanFilterCache) { scope.launch { store.setAutoCleanFilterCache(it) } }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_group_privacy),
                    items = listOf(
                        stringResource(R.string.settings_privacy_desc_1),
                        stringResource(R.string.settings_privacy_desc_2)
                    )
                ) {
                    SwitchSetting(stringResource(R.string.settings_precise_location), settings.preciseLocationMode) { scope.launch { store.setPreciseLocationMode(it) } }
                    SwitchSetting(stringResource(R.string.settings_share_privacy_check), settings.sharePrivacyCheck) { scope.launch { store.setSharePrivacyCheck(it) } }
                    SwitchSetting(stringResource(R.string.settings_allow_backup), settings.allowBackup) { scope.launch { store.setAllowBackup(it) } }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_group_about),
                    items = listOf(
                        stringResource(R.string.settings_about_desc_1),
                        stringResource(R.string.settings_about_desc_2)
                    )
                ) {
                    SwitchSetting(stringResource(R.string.settings_show_build_info), settings.includeBuildInfo) { scope.launch { store.setIncludeBuildInfo(it) } }
                    SwitchSetting(stringResource(R.string.settings_show_license_info), settings.includeLicenseInfo) { scope.launch { store.setIncludeLicenseInfo(it) } }
                    SwitchSetting(stringResource(R.string.settings_show_feedback_entry), settings.showFeedbackEntry) { scope.launch { store.setShowFeedbackEntry(it) } }
                    if (settings.includeBuildInfo) {
                        Text(
                            text = stringResource(R.string.settings_build_info_value, versionName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (settings.includeLicenseInfo) {
                        Text(
                            text = stringResource(R.string.settings_license_info_value),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (settings.showFeedbackEntry) {
                        Text(
                            text = stringResource(R.string.settings_feedback_entry_value),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val githubUrl = stringResource(R.string.github_url)
                    val annotatedString = buildAnnotatedString {
                        append("Github : ")
                        withLink(LinkAnnotation.Url(githubUrl)) {
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append(githubUrl)
                            }
                        }
                    }

                    Text(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    items: List<String>,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            items.forEach {
                Text(text = "• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            content()
        }
    }
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, modifier = Modifier.fillMaxWidth(0.78f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    onChanged: (Float) -> Unit,
    valueText: String,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title)
        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = range,
            steps = steps
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
