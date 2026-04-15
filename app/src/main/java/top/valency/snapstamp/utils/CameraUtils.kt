package top.valency.snapstamp.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
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
import androidx.core.graphics.createBitmap

// 拍照权限处理保持不变
fun takePhotoWithPermission(
    context: Context,
    client: FusedLocationProviderClient,
    includeLocation: Boolean,
    onResult: (android.location.Location?) -> Unit
) {
    if (!includeLocation) {
        onResult(null)
        return
    }
    val fineLoc = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
    val coarseLoc = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    if (fineLoc == android.content.pm.PackageManager.PERMISSION_GRANTED || coarseLoc == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        try { client.lastLocation.addOnCompleteListener { task -> onResult(if (task.isSuccessful) task.result else null) } }
        catch (_: SecurityException) { onResult(null) }
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
    loc: android.location.Location?,
    jpegQuality: Int = 100,
    saveInternalCopy: Boolean = true,
    autoSaveToAlbum: Boolean = true,
    borderStrength: Float = 0.5f,
    borderClassicStyle: Boolean = true,
    infoVisibleOverlay: Boolean = false
): Bitmap? = withContext(Dispatchers.IO) {

    // --- 第一部分：图像裁剪与旋转 (超清重写版) ---
    // 1. 获取旋转后理应得到的宽高（但不实际创建巨大的旋转图片）
    val isTransposed = rotation == 90 || rotation == 270
    val rotatedW = if (isTransposed) original.height else original.width
    val rotatedH = if (isTransposed) original.width else original.height

    // 2. 根据取景框的缩放规则（CenterCrop）推算缩放比例和偏移量
    val scale = maxOf(viewW.toFloat() / rotatedW, viewH.toFloat() / rotatedH)
    val dx = (viewW - rotatedW * scale) / 2f
    val dy = (viewH - rotatedH * scale) / 2f

    // 3. 将屏幕上的裁剪框精准映射回高分辨率原图上的坐标
    val cropLeft = ((screenRect.left - dx) / scale).toInt().coerceAtLeast(0)
    val cropTop = ((screenRect.top - dy) / scale).toInt().coerceAtLeast(0)
    val cropSize = (screenRect.width() / scale).toInt()
        .coerceAtMost(rotatedW - cropLeft)
        .coerceAtMost(rotatedH - cropTop)

    if (cropSize <= 0) {
        Log.e("processAndSaveStamp", "裁剪尺寸无效: $cropSize")
        return@withContext null
    }

    // 4. 一次性无损裁剪：直接在目标尺寸的画布上进行矩阵变换和绘制
    // 这样做避免了全图旋转造成的极高内存开销和像素插值损失
    val cropped = createBitmap(cropSize, cropSize)
    val canvas = Canvas(cropped)

    val matrix = Matrix()
    matrix.postTranslate(-original.width / 2f, -original.height / 2f) // 移至原点准备旋转
    matrix.postRotate(rotation.toFloat()) // 旋转
    matrix.postTranslate(rotatedW / 2f, rotatedH / 2f) // 移回正象限
    matrix.postTranslate(-cropLeft.toFloat(), -cropTop.toFloat()) // 移动到裁剪区域

    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true // 开启双线性过滤保证平滑
        isDither = true
    }
    canvas.drawBitmap(original, matrix, paint)

    // 可以释放原图内存了（防 OOM）
    original.recycle()

    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val baseFileName = "STAMP_$timeStr"

    try {
        // --- 第二部分：保存到 App 私有目录 (.jpg) ---
        if (saveInternalCopy) {
            val internalFile = File(context.filesDir, "$baseFileName.jpg")
            FileOutputStream(internalFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(70, 100), out)
            }

            // 写入 EXIF 信息
            val exif = ExifInterface(internalFile.absolutePath)
            loc?.let { exif.setGpsInfo(it) }
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
            exif.setAttribute(ExifInterface.TAG_DATETIME, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date()))
            exif.saveAttributes()
        }

        // --- 第三部分：合成带边框邮票并保存到相册 (.png) ---
        val stampBitmap = createStampBitmap(
            cropped = cropped,
            borderStrength = borderStrength,
            classicStyle = borderClassicStyle,
            showInfoOverlay = infoVisibleOverlay
        )

        if (autoSaveToAlbum) {
            // 插入到系统相册
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$baseFileName.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(collection, values)

            uri?.let { targetUri ->
                context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                    stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }
        }

        return@withContext stampBitmap

    } catch (e: Exception) {
        Log.e("processAndSaveStamp", "保存失败", e)
        return@withContext null
    }
}


/**
 * 应用“油画风格”滤镜 (相邻像素灰度分组算法)
 * @param src 原始 Bitmap
 * @param radius 采样半径 (值越大，笔触色块越大)
 * @param levels 色彩/灰度层级 (值越小，色彩越扁平化，油画感越强)
 */
suspend fun applyOilPaintingFilter(src: Bitmap, radius: Int = 4, levels: Int = 20): Bitmap = withContext(Dispatchers.Default) {
    val width = src.width
    val height = src.height
    // 创建与原图等大的画布
    val dest = createBitmap(width, height)

    // 使用 IntArray 批量处理像素，极大地提升遍历性能，避免 getPixel/setPixel 的开销
    val srcPixels = IntArray(width * height)
    val destPixels = IntArray(width * height)
    src.getPixels(srcPixels, 0, width, 0, 0, width, height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val intensityCount = IntArray(levels)
            val intensityR = IntArray(levels)
            val intensityG = IntArray(levels)
            val intensityB = IntArray(levels)

            var maxCount = 0
            var maxIndex = 0

            // 遍历相邻像素半径区域
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, width - 1)
                    val ny = (y + dy).coerceIn(0, height - 1)
                    val color = srcPixels[ny * width + nx]

                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF

                    // 计算该像素的灰度等级分组
                    val intensity = (((r + g + b) / 3.0) * levels / 256.0).toInt().coerceIn(0, levels - 1)

                    intensityCount[intensity]++
                    intensityR[intensity] += r
                    intensityG[intensity] += g
                    intensityB[intensity] += b

                    // 找出出现次数最多的灰度分组
                    if (intensityCount[intensity] > maxCount) {
                        maxCount = intensityCount[intensity]
                        maxIndex = intensity
                    }
                }
            }

            // 计算该高频分组的平均 RGB 颜色
            val outR = intensityR[maxIndex] / maxCount
            val outG = intensityG[maxIndex] / maxCount
            val outB = intensityB[maxIndex] / maxCount

            // 组合并设置目标像素
            destPixels[y * width + x] = (-0x1000000) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    dest.setPixels(destPixels, 0, width, 0, 0, width, height)
    return@withContext dest
}