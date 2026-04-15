package top.valency.snapstamp.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.exifinterface.media.ExifInterface

// 别名简化
typealias ComposeColor = Color

// Base64 处理中文备注
fun encodeRemark(text: String): String = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
fun decodeRemark(base64: String): String = try { String(Base64.decode(base64, Base64.NO_WRAP), Charsets.UTF_8) } catch (e: Exception) { base64 }

// 拷贝照片 Exif 信息的工具方法
fun copyExif(sourcePath: String, destPath: String) {
    try {
        val oldExif = ExifInterface(sourcePath)
        val newExif = ExifInterface(destPath)
        listOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_MODEL,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_USER_COMMENT
        ).forEach { tag -> oldExif.getAttribute(tag)?.let { newExif.setAttribute(tag, it) } }
        newExif.saveAttributes()
    } catch (e: Exception) { Log.e("Exif", "Copy failed", e) }
}

// 绘制并生成带有锯齿孔洞的白边邮票位图
fun createStampBitmap(
    cropped: Bitmap,
    borderStrength: Float = 0.5f,
    classicStyle: Boolean = true,
    showInfoOverlay: Boolean = false
): Bitmap {
    val cropSize = cropped.width
    val normalizedBorder = borderStrength.coerceIn(0f, 1f)
    val padding = cropSize * (0.03f + normalizedBorder * 0.06f)
    val finalSize = cropSize + (padding * 2).toInt()
    val stampBitmap = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(stampBitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, finalSize.toFloat(), finalSize.toFloat(), paint)
    canvas.drawBitmap(cropped, padding, padding, null)

    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    val holeRadius = finalSize * if (classicStyle) 0.015f else 0.01f
    val spacing = finalSize * if (classicStyle) 0.045f else 0.06f
    val stepCount = (finalSize / spacing).toInt().coerceAtLeast(1)
    val actualSpacing = finalSize.toFloat() / stepCount

    for (i in 0..stepCount) {
        val center = i * actualSpacing
        canvas.drawCircle(center, 0f, holeRadius, paint)
        canvas.drawCircle(center, finalSize.toFloat(), holeRadius, paint)
        canvas.drawCircle(0f, center, holeRadius, paint)
        canvas.drawCircle(finalSize.toFloat(), center, holeRadius, paint)
    }

    if (showInfoOverlay) {
        paint.xfermode = null
        paint.color = android.graphics.Color.argb(200, 0, 0, 0)
        paint.textSize = finalSize * 0.045f
        canvas.drawText("SnapStamp", padding, finalSize - padding / 2f, paint)
    }
    return stampBitmap
}