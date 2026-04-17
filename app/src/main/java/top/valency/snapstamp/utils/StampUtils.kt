package top.valency.snapstamp.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale

// 别名简化
typealias ComposeColor = Color

fun encodeRemark(text: String): String = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
fun decodeRemark(base64: String): String = try { String(Base64.decode(base64, Base64.NO_WRAP), Charsets.UTF_8) } catch (e: Exception) { base64 }

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

/**
 * 生成现代化审美的邮票位图 (极简风格、动态比例、高级排版)
 */
fun createStampBitmap(
    cropped: Bitmap,
    date: String,
    borderStrength: Float = 0.5f,
    classicStyle: Boolean = true, // 预留参数，可用于切换不同风格
    showInfoOverlay: Boolean = false,
    deviceInfo: String = "",
    location: String = ""
): Bitmap {
    // 1. 日期格式化 (更现代的显示方式，如 "Apr 17, 2026")
    val formattedDate = try {
        val inputFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.ENGLISH)
        val dateObj = inputFormat.parse(date)
        dateObj?.let { outputFormat.format(it).uppercase() } ?: date
    } catch (e: Exception) {
        date
    }

    // 2. 自适应尺寸计算 (保留原图比例)
    val baseWidth = cropped.width
    val baseHeight = cropped.height

    // 边距计算：顶部和左右较窄，底部留大片空白用于排版（拍立得/艺术展签风格）
    val normalizedBorder = borderStrength.coerceIn(0f, 1f)
    val sidePadding = (baseWidth * (0.04f + normalizedBorder * 0.04f)).toInt()
    val bottomPadding = (baseWidth * 0.18f).toInt() // 底部黄金比例留白

    val finalWidth = baseWidth + (sidePadding * 2)
    val finalHeight = baseHeight + sidePadding + bottomPadding

    // 创建支持透明度的 Bitmap (以便孔洞变透明)
    val stampBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(stampBitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 3. 绘制背景：使用高级纸白色 (略微带一点暖色调，摒弃死白或冷灰)
    paint.color = Color.rgb(252, 252, 250)
    canvas.drawRect(0f, 0f, finalWidth.toFloat(), finalHeight.toFloat(), paint)

    // 4. 绘制照片本体
    val destRect = RectF(
        sidePadding.toFloat(),
        sidePadding.toFloat(),
        (sidePadding + baseWidth).toFloat(),
        (sidePadding + baseHeight).toFloat()
    )
    canvas.drawBitmap(cropped, null, destRect, paint)

    // 4.1 绘制照片内描边 (微弱的灰线，增加立体感和精致感，防止白色相片融入背景)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = finalWidth * 0.0015f
    paint.color = Color.argb(30, 0, 0, 0) // 12% 黑色透明度
    canvas.drawRect(destRect, paint)
    paint.style = Paint.Style.FILL // 恢复填充模式

    // 5. 绘制边缘锯齿孔洞 (精确计算，确保首尾完美对称)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    val holeRadius = finalWidth * 0.009f // 更精致的小孔
    val spacing = finalWidth * 0.032f    // 间距

    // 顶底边缘
    val stepCountX = (finalWidth / spacing).toInt().coerceAtLeast(1)
    val actualSpacingX = finalWidth.toFloat() / stepCountX
    for (i in 0..stepCountX) {
        val cx = i * actualSpacingX
        canvas.drawCircle(cx, 0f, holeRadius, paint)
        canvas.drawCircle(cx, finalHeight.toFloat(), holeRadius, paint)
    }

    // 左右边缘
    val stepCountY = (finalHeight / spacing).toInt().coerceAtLeast(1)
    val actualSpacingY = finalHeight.toFloat() / stepCountY
    for (i in 0..stepCountY) {
        val cy = i * actualSpacingY
        canvas.drawCircle(0f, cy, holeRadius, paint)
        canvas.drawCircle(finalWidth.toFloat(), cy, holeRadius, paint)
    }
    paint.xfermode = null // 恢复模式

    // 6. 现代版块信息绘制 (Leica / 画廊排版风格)
    // 布局：左侧 -> 日期 & 位置 ； 右侧 -> 设备信息
    val textYStart = finalHeight - bottomPadding + (bottomPadding * 0.45f)
    val sideMargin = sidePadding * 1.2f

    // --- 左侧排版 (日期 & 位置) ---
    paint.textAlign = Paint.Align.LEFT

    // 日期 (深灰色，Medium 粗细，英文字母大写提升设计感)
    paint.color = Color.rgb(40, 40, 40)
    paint.textSize = finalWidth * 0.032f
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    canvas.drawText(formattedDate, sideMargin, textYStart, paint)

    // 位置 (浅灰色，常规体，位于日期下方)
    if (location.isNotBlank()) {
        paint.color = Color.rgb(140, 140, 140)
        paint.textSize = finalWidth * 0.024f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText(location, sideMargin, textYStart + (finalWidth * 0.045f), paint)
    }

    // --- 右侧排版 (设备信息) ---
    if (deviceInfo.isNotBlank()) {
        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.rgb(60, 60, 60)

        // 提取设备名的品牌部分加粗，型号常规 (如果需要极简，这里直接统一绘制即可)
        paint.textSize = finalWidth * 0.032f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        // 设备信息垂直居中于底部区域
        val deviceY = textYStart + if (location.isNotBlank()) (finalWidth * 0.02f) else 0f
        canvas.drawText(deviceInfo, finalWidth - sideMargin, deviceY, paint)
    }

    return stampBitmap
}