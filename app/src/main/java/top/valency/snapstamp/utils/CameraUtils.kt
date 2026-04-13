package top.valency.snapstamp.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun takePhotoWithPermission(context: Context, client: FusedLocationProviderClient, onResult: (android.location.Location?) -> Unit) {
    val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
        try { client.lastLocation.addOnCompleteListener { task -> onResult(if (task.isSuccessful) task.result else null) } }
        catch (e: SecurityException) { onResult(null) }
    } else { onResult(null) }
}

suspend fun processAndSaveStamp(
    original: Bitmap, rotation: Int, context: Context,
    screenRect: RectF, viewW: Int, viewH: Int, loc: android.location.Location?
) = withContext(Dispatchers.Default) {
    val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

    val scale = maxOf(viewW.toFloat() / rotated.width, viewH.toFloat() / rotated.height)
    val dx = (viewW - rotated.width * scale) / 2f
    val dy = (viewH - rotated.height * scale) / 2f

    val cropLeft = ((screenRect.left - dx) / scale).toInt().coerceAtLeast(0)
    val cropTop = ((screenRect.top - dy) / scale).toInt().coerceAtLeast(0)
    val cropSize = (screenRect.width() / scale).toInt()
        .coerceAtMost(rotated.width - cropLeft)
        .coerceAtMost(rotated.height - cropTop)

    val cropped = Bitmap.createBitmap(rotated, cropLeft, cropTop, cropSize, cropSize)

    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val baseFileName = "STAMP_$timeStr"

    try {
        // 保存App内部无边框原图 (.jpg)
        val internalFile = File(context.filesDir, "$baseFileName.jpg")
        FileOutputStream(internalFile).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 95, out) }

        val exif = ExifInterface(internalFile.absolutePath)
        loc?.let { exif.setGpsInfo(it) }
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        exif.setAttribute(ExifInterface.TAG_DATETIME, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date()))
        exif.saveAttributes()

        // 绘制带边框的邮票相册版 (.png)
        val stampBitmap = createStampBitmap(cropped)
        val tempExportFile = File(context.cacheDir, "$baseFileName.png")
        FileOutputStream(tempExportFile).use { out -> stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

        copyExif(internalFile.absolutePath, tempExportFile.absolutePath)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, tempExportFile.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { u -> context.contentResolver.openOutputStream(u)?.use { it.write(tempExportFile.readBytes()) } }
        tempExportFile.delete()

    } catch (e: Exception) { Log.e("Save", "Fail", e) }
}