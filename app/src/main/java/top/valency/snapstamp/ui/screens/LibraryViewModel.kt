package top.valency.snapstamp.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.valency.snapstamp.data.AppSettings
import top.valency.snapstamp.data.repository.StampRepository
import top.valency.snapstamp.model.StampModel
import top.valency.snapstamp.utils.applyOilPaintingFilter
import top.valency.snapstamp.utils.createStampBitmap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.exifinterface.media.ExifInterface

class LibraryViewModel(
    private val repository: StampRepository,
    private val context: Context // In a real app, use Hilt or AndroidViewModel for Context
) : ViewModel() {

    private val _stamps = MutableStateFlow<List<StampModel>>(emptyList())
    val stamps: StateFlow<List<StampModel>> = _stamps.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    init {
        loadStamps()
    }

    fun loadStamps() {
        viewModelScope.launch {
            _isOperating.value = true
            _stamps.value = repository.getStamps()
            _isOperating.value = false
        }
    }

    fun deleteStamps(stampsToDelete: List<StampModel>, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isOperating.value = true
            repository.deleteStamps(stampsToDelete)
            loadStamps()
            _isOperating.value = false
            onComplete()
        }
    }

    fun updateRemark(stamp: StampModel, remark: String) {
        viewModelScope.launch {
            repository.updateRemark(stamp, remark)
            loadStamps()
        }
    }

    suspend fun generateOilFilterFile(stamp: StampModel, settings: AppSettings): File? = withContext(Dispatchers.IO) {
        val oilFile = File(stamp.file.absolutePath.replace(".jpg", "_OIL.jpg"))
        if (settings.filterCacheEnabled && oilFile.exists()) {
            return@withContext oilFile
        }

        _isOperating.value = true
        val original = BitmapFactory.decodeFile(stamp.file.absolutePath) ?: return@withContext null
        
        val maxDim = 800f
        val scale = minOf(1f, maxDim / maxOf(original.width, original.height))
        val processBitmap = if (scale < 1f) {
            val scaled = original.scale(
                (original.width * scale).toInt(),
                (original.height * scale).toInt()
            )
            original.recycle()
            scaled
        } else {
            original
        }
        
        val radius = (4 + settings.oilFilterStrength * 6).toInt().coerceIn(4, 10)
        val levels = (26 - settings.oilFilterStrength * 12).toInt().coerceIn(10, 26)
        val filtered = applyOilPaintingFilter(processBitmap, radius = radius, levels = levels)
        
        val targetFile = if (settings.filterCacheEnabled) {
            oilFile
        } else {
            File(context.cacheDir, "TEMP_OIL_${System.currentTimeMillis()}.jpg")
        }
        
        FileOutputStream(targetFile).use { out -> filtered.compress(Bitmap.CompressFormat.JPEG, 100, out) }
        processBitmap.recycle()
        filtered.recycle()
        _isOperating.value = false
        targetFile
    }

    fun saveToAlbum(stampsToSave: List<StampModel>, withBorder: Boolean, currentDisplayFile: File?, settings: AppSettings, onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isOperating.value = true
            stampsToSave.forEach { stamp ->
                try {
                    val fileToProcess = if (stampsToSave.size == 1) currentDisplayFile ?: stamp.file else stamp.file
                    val isOil = fileToProcess.name.contains("_OIL")
                    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                    val displayName = if (withBorder) "STAMP_${if(isOil)"OIL_" else ""}$timeStr.png" else "PHOTO_${if(isOil)"OIL_" else ""}$timeStr.jpg"
                    
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, if (withBorder) "image/png" else "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
                    }
                    
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            if (withBorder) {
                                val bitmap = BitmapFactory.decodeFile(fileToProcess.absolutePath)
                                val stamped = createStampBitmap(
                                    cropped = bitmap,
                                    borderStrength = settings.borderThickness,
                                    classicStyle = settings.borderClassicStyle,
                                    showInfoOverlay = settings.infoVisibleOverlay,
                                    date = stamp.date,
                                    deviceInfo = stamp.info,
                                    location = stamp.location
                                )
                                stamped.compress(Bitmap.CompressFormat.PNG, 100, out)
                            } else {
                                FileInputStream(fileToProcess).use { it.copyTo(out) }
                            }
                        }
                    }
                } catch (e: Exception) { Log.e("Save", "Error", e) }
            }
            _isOperating.value = false
            withContext(Dispatchers.Main) {
                onComplete("Saved to album")
            }
        }
    }

    fun shareImages(stampsToShare: List<StampModel>, withBorder: Boolean, currentDisplayFile: File?, settings: AppSettings, onIntentReady: (android.content.Intent) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isOperating.value = true
            try {
                val uris = ArrayList<Uri>()
                stampsToShare.forEach { stamp ->
                    val fileToProcess = if (stampsToShare.size == 1) currentDisplayFile ?: stamp.file else stamp.file
                    val fileToShare = if (withBorder) {
                        val bitmap = BitmapFactory.decodeFile(fileToProcess.absolutePath)
                        val stamped = createStampBitmap(
                            cropped = bitmap,
                            borderStrength = settings.borderThickness,
                            classicStyle = settings.borderClassicStyle,
                            showInfoOverlay = settings.infoVisibleOverlay,
                            date = stamp.date,
                            deviceInfo = stamp.info,
                            location = stamp.location
                        )
                        val temp = File(context.cacheDir, "SHARE_${System.currentTimeMillis()}.png")
                        FileOutputStream(temp).use { stamped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        temp
                    } else {
                        if (!settings.sharePrivacyCheck) {
                            fileToProcess
                        } else {
                            val temp = File(context.cacheDir, "SHARE_PRIV_${System.currentTimeMillis()}.jpg")
                            FileInputStream(fileToProcess).use { input ->
                                FileOutputStream(temp).use { output -> input.copyTo(output) }
                            }
                            try {
                                val exif = ExifInterface(temp.absolutePath)
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, null)
                                exif.saveAttributes()
                            } catch (_: Exception) { }
                            temp
                        }
                    }
                    uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileToShare))
                }
                val intent = android.content.Intent(if (uris.size > 1) android.content.Intent.ACTION_SEND_MULTIPLE else android.content.Intent.ACTION_SEND).apply {
                    type = "image/*"
                    if (uris.size > 1) putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                    else putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    onIntentReady(intent)
                }
            } catch (_: Exception) { }
            _isOperating.value = false
        }
    }
}
