package top.valency.snapstamp.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 拍照权限处理保持不变
fun takePhotoWithPermission(context: Context, client: FusedLocationProviderClient, onResult: (android.location.Location?) -> Unit) {
    val fineLoc = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
    val coarseLoc = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    if (fineLoc == android.content.pm.PackageManager.PERMISSION_GRANTED || coarseLoc == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        try { client.lastLocation.addOnCompleteListener { task -> onResult(if (task.isSuccessful) task.result else null) } }
        catch (e: SecurityException) { onResult(null) }
    } else { onResult(null) }
}

/**
 * 处理并保存邮票
 * 1. 保存原始裁剪图到 App 私有目录 (用于 LibraryScreen 显示)
 * 2. 合成邮票效果图并保存到系统相册 (用于用户分享)
 * 3. 返回合成后的 Bitmap 用于 UI 动画
 */
suspend fun processAndSaveStamp(
    original: Bitmap,
    rotation: Int,
    context: Context,
    screenRect: RectF,
    viewW: Int,
    viewH: Int,
    loc: android.location.Location?
): Bitmap? = withContext(Dispatchers.Default) {

    // --- 第一部分：图像裁剪与旋转 ---
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

    val scale = maxOf(viewW.toFloat() / rotated.width, viewH.toFloat() / rotated.height)
    val dx = (viewW - rotated.width * scale) / 2f
    val dy = (viewH - rotated.height * scale) / 2f

    val cropLeft = ((screenRect.left - dx) / scale).toInt().coerceAtLeast(0)
    val cropTop = ((screenRect.top - dy) / scale).toInt().coerceAtLeast(0)
    val cropSize = (screenRect.width() / scale).toInt()
        .coerceAtMost(rotated.width - cropLeft)
        .coerceAtMost(rotated.height - cropTop)

    // 最终得到的正方形裁剪图 (无边框原图)
    val cropped = Bitmap.createBitmap(rotated, cropLeft, cropTop, cropSize, cropSize)

    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val baseFileName = "STAMP_$timeStr"

    try {
        // --- 第二部分：保存到 App 私有目录 (.jpg) ---
        // 这是 App 内部集邮册的数据源
        val internalFile = File(context.filesDir, "$baseFileName.jpg")
        FileOutputStream(internalFile).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        // 写入 EXIF 信息到私有文件
        val exif = ExifInterface(internalFile.absolutePath)
        loc?.let { exif.setGpsInfo(it) }
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        exif.setAttribute(ExifInterface.TAG_DATETIME, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date()))
        exif.saveAttributes()

        // --- 第三部分：合成带边框邮票并保存到相册 (.png) ---
        val stampBitmap = createStampBitmap(cropped)

        // 插入到系统相册 (MediaStore)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$baseFileName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            // Android 10+ 放置在特定文件夹下，无需存储权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values)

        uri?.let { targetUri ->
            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }

            // 尝试将私有文件的 EXIF 拷贝到相册文件 (注: PNG 的 EXIF 兼容性视系统版本而定)
            // 如果 copyExif 内部实现是基于路径的，这里可能需要传入具体路径
            // 但 MediaStore 写入后通常由系统管理，这里可选
        }

        // 返回合成后的 Bitmap 用于 UI 展示
        return@withContext stampBitmap

    } catch (e: Exception) {
        Log.e("processAndSaveStamp", "保存失败", e)
        return@withContext null
    }
}