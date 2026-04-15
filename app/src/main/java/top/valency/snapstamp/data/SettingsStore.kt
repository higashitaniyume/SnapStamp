package top.valency.snapstamp.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "snapstamp_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val defaultZoom = floatPreferencesKey("default_zoom")
        val defaultExposure = intPreferencesKey("default_exposure")
        val focusAutoResetSec = intPreferencesKey("focus_auto_reset_sec")
        val shutterFeedback = booleanPreferencesKey("shutter_feedback")
        val framingGuide = booleanPreferencesKey("framing_guide")
        val dropAnimation = booleanPreferencesKey("drop_animation")
        val exportWithBorderDefault = booleanPreferencesKey("export_with_border_default")
        val borderThickness = floatPreferencesKey("border_thickness")
        val borderClassicStyle = booleanPreferencesKey("border_classic_style")
        val writeLocationExif = booleanPreferencesKey("write_location_exif")
        val writeRemarkExif = booleanPreferencesKey("write_remark_exif")
        val infoVisibleOverlay = booleanPreferencesKey("info_visible_overlay")
        val previewOilAsDefault = booleanPreferencesKey("preview_oil_as_default")
        val oilFilterStrength = floatPreferencesKey("oil_filter_strength")
        val filterCacheEnabled = booleanPreferencesKey("filter_cache_enabled")
        val largeImageWarn = booleanPreferencesKey("large_image_warn")
        val previewGestureFast = booleanPreferencesKey("preview_gesture_fast")
        val jpegQuality = intPreferencesKey("jpeg_quality")
        val autoSaveAfterShot = booleanPreferencesKey("auto_save_after_shot")
        val saveToSystemAlbum = booleanPreferencesKey("save_to_system_album")
        val keepInternalCopy = booleanPreferencesKey("keep_internal_copy")
        val autoCleanFilterCache = booleanPreferencesKey("auto_clean_filter_cache")
        val preciseLocationMode = booleanPreferencesKey("precise_location_mode")
        val sharePrivacyCheck = booleanPreferencesKey("share_privacy_check")
        val allowBackup = booleanPreferencesKey("allow_backup")
        val includeBuildInfo = booleanPreferencesKey("include_build_info")
        val includeLicenseInfo = booleanPreferencesKey("include_license_info")
        val showFeedbackEntry = booleanPreferencesKey("show_feedback_entry")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            defaultZoom = prefs[Keys.defaultZoom] ?: 0.5f,
            defaultExposure = prefs[Keys.defaultExposure] ?: 0,
            focusAutoResetSec = prefs[Keys.focusAutoResetSec] ?: 10,
            shutterFeedback = prefs[Keys.shutterFeedback] ?: true,
            framingGuide = prefs[Keys.framingGuide] ?: true,
            dropAnimation = prefs[Keys.dropAnimation] ?: true,
            exportWithBorderDefault = prefs[Keys.exportWithBorderDefault] ?: true,
            borderThickness = prefs[Keys.borderThickness] ?: 0.5f,
            borderClassicStyle = prefs[Keys.borderClassicStyle] ?: true,
            writeLocationExif = prefs[Keys.writeLocationExif] ?: true,
            writeRemarkExif = prefs[Keys.writeRemarkExif] ?: true,
            infoVisibleOverlay = prefs[Keys.infoVisibleOverlay] ?: false,
            previewOilAsDefault = prefs[Keys.previewOilAsDefault] ?: false,
            oilFilterStrength = prefs[Keys.oilFilterStrength] ?: 0.55f,
            filterCacheEnabled = prefs[Keys.filterCacheEnabled] ?: true,
            largeImageWarn = prefs[Keys.largeImageWarn] ?: true,
            previewGestureFast = prefs[Keys.previewGestureFast] ?: false,
            jpegQuality = prefs[Keys.jpegQuality] ?: 100,
            autoSaveAfterShot = prefs[Keys.autoSaveAfterShot] ?: true,
            saveToSystemAlbum = prefs[Keys.saveToSystemAlbum] ?: true,
            keepInternalCopy = prefs[Keys.keepInternalCopy] ?: true,
            autoCleanFilterCache = prefs[Keys.autoCleanFilterCache] ?: false,
            preciseLocationMode = prefs[Keys.preciseLocationMode] ?: true,
            sharePrivacyCheck = prefs[Keys.sharePrivacyCheck] ?: true,
            allowBackup = prefs[Keys.allowBackup] ?: true,
            includeBuildInfo = prefs[Keys.includeBuildInfo] ?: true,
            includeLicenseInfo = prefs[Keys.includeLicenseInfo] ?: true,
            showFeedbackEntry = prefs[Keys.showFeedbackEntry] ?: true
        )
    }

    suspend fun setDefaultZoom(v: Float) = setFloat(Keys.defaultZoom, v)
    suspend fun setDefaultExposure(v: Int) = setInt(Keys.defaultExposure, v)
    suspend fun setFocusAutoResetSec(v: Int) = setInt(Keys.focusAutoResetSec, v)
    suspend fun setShutterFeedback(v: Boolean) = setBoolean(Keys.shutterFeedback, v)
    suspend fun setFramingGuide(v: Boolean) = setBoolean(Keys.framingGuide, v)
    suspend fun setDropAnimation(v: Boolean) = setBoolean(Keys.dropAnimation, v)
    suspend fun setExportWithBorderDefault(v: Boolean) = setBoolean(Keys.exportWithBorderDefault, v)
    suspend fun setBorderThickness(v: Float) = setFloat(Keys.borderThickness, v)
    suspend fun setBorderClassicStyle(v: Boolean) = setBoolean(Keys.borderClassicStyle, v)
    suspend fun setWriteLocationExif(v: Boolean) = setBoolean(Keys.writeLocationExif, v)
    suspend fun setWriteRemarkExif(v: Boolean) = setBoolean(Keys.writeRemarkExif, v)
    suspend fun setInfoVisibleOverlay(v: Boolean) = setBoolean(Keys.infoVisibleOverlay, v)
    suspend fun setPreviewOilAsDefault(v: Boolean) = setBoolean(Keys.previewOilAsDefault, v)
    suspend fun setOilFilterStrength(v: Float) = setFloat(Keys.oilFilterStrength, v)
    suspend fun setFilterCacheEnabled(v: Boolean) = setBoolean(Keys.filterCacheEnabled, v)
    suspend fun setLargeImageWarn(v: Boolean) = setBoolean(Keys.largeImageWarn, v)
    suspend fun setPreviewGestureFast(v: Boolean) = setBoolean(Keys.previewGestureFast, v)
    suspend fun setJpegQuality(v: Int) = setInt(Keys.jpegQuality, v)
    suspend fun setAutoSaveAfterShot(v: Boolean) = setBoolean(Keys.autoSaveAfterShot, v)
    suspend fun setSaveToSystemAlbum(v: Boolean) = setBoolean(Keys.saveToSystemAlbum, v)
    suspend fun setKeepInternalCopy(v: Boolean) = setBoolean(Keys.keepInternalCopy, v)
    suspend fun setAutoCleanFilterCache(v: Boolean) = setBoolean(Keys.autoCleanFilterCache, v)
    suspend fun setPreciseLocationMode(v: Boolean) = setBoolean(Keys.preciseLocationMode, v)
    suspend fun setSharePrivacyCheck(v: Boolean) = setBoolean(Keys.sharePrivacyCheck, v)
    suspend fun setAllowBackup(v: Boolean) = setBoolean(Keys.allowBackup, v)
    suspend fun setIncludeBuildInfo(v: Boolean) = setBoolean(Keys.includeBuildInfo, v)
    suspend fun setIncludeLicenseInfo(v: Boolean) = setBoolean(Keys.includeLicenseInfo, v)
    suspend fun setShowFeedbackEntry(v: Boolean) = setBoolean(Keys.showFeedbackEntry, v)

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun setFloat(key: Preferences.Key<Float>, value: Float) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.settingsDataStore.edit { it[key] = value }
    }
}
