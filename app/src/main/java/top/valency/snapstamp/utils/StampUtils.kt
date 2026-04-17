package top.valency.snapstamp.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface

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

// 绘制并生成带有锯齿孔洞的竖版白边邮票位图
fun createStampBitmap(
    cropped: Bitmap,
    date: String, // 【新增】传入你想印上去的日期字符串
    borderStrength: Float = 0.5f,
    classicStyle: Boolean = true,
    showInfoOverlay: Boolean = false
): Bitmap {
    // 1. 确定最终邮票的尺寸 (采用 3:4 的竖版比例)
    val baseWidth = cropped.width
    val normalizedBorder = borderStrength.coerceIn(0f, 1f)
    val padding = baseWidth * (0.03f + normalizedBorder * 0.06f)

    val finalWidth = baseWidth + (padding * 2).toInt()
    val finalHeight = (finalWidth * 4f / 3f).toInt() // 【修改】强制高度为宽度的 4/3

    val stampBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(stampBitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 2. 绘制纯白底色
    paint.color = Color.WHITE
    canvas.drawRect(0f, 0f, finalWidth.toFloat(), finalHeight.toFloat(), paint)

    // 3. 计算图片绘制区域：保持比例，在上方区域居中 (相当于 ContentScale.Fit)
    val bottomTextSpace = finalWidth * 0.15f // 底部预留给文字的空间
    val imageAreaRect = RectF(
        padding,
        padding,
        finalWidth - padding,
        finalHeight - bottomTextSpace - padding // 减去底部留白
    )

    // 计算图片的缩放比例
    val scale = Math.min(
        imageAreaRect.width() / cropped.width,
        imageAreaRect.height() / cropped.height
    )
    val scaledWidth = cropped.width * scale
    val scaledHeight = cropped.height * scale

    // 计算居中的偏移量
    val dx = imageAreaRect.left + (imageAreaRect.width() - scaledWidth) / 2f
    val dy = imageAreaRect.top + (imageAreaRect.height() - scaledHeight) / 2f

    val destRect = RectF(dx, dy, dx + scaledWidth, dy + scaledHeight)
    canvas.drawBitmap(cropped, null, destRect, paint) // 将图片画在计算好的矩形内

    // 4. 绘制四周的锯齿孔洞
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    val holeRadius = finalWidth * if (classicStyle) 0.015f else 0.01f
    val spacing = finalWidth * if (classicStyle) 0.045f else 0.06f

    // 顶/底边孔洞 (使用宽度计算步数)
    val stepCountX = (finalWidth / spacing).toInt().coerceAtLeast(1)
    val actualSpacingX = finalWidth.toFloat() / stepCountX
    for (i in 0..stepCountX) {
        val center = i * actualSpacingX
        canvas.drawCircle(center, 0f, holeRadius, paint)
        canvas.drawCircle(center, finalHeight.toFloat(), holeRadius, paint)
    }

    // 左/右边孔洞 (使用高度计算步数)
    val stepCountY = (finalHeight / spacing).toInt().coerceAtLeast(1)
    val actualSpacingY = finalHeight.toFloat() / stepCountY
    for (i in 0..stepCountY) {
        val center = i * actualSpacingY
        canvas.drawCircle(0f, center, holeRadius, paint)
        canvas.drawCircle(finalWidth.toFloat(), center, holeRadius, paint)
    }

    // 5. 绘制底部的文字（日期 + 可选水印）
    paint.xfermode = null
    paint.color = Color.DKGRAY
    paint.textSize = finalWidth * 0.045f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) // 文字加粗

    // 确定文字 Y 坐标（放在底部留白空间的垂直居中位置）
    val textY = finalHeight - padding - (bottomTextSpace - paint.textSize) / 2f

    // 【修改】绘制日期，靠左对齐 (X位置就是左侧 padding 的位置)
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText(date, padding, textY, paint)

    if (showInfoOverlay) {
        paint.color = Color.argb(120, 0, 0, 0)
        paint.textSize = finalWidth * 0.035f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("SnapStamp", finalWidth - padding, textY, paint)
    }

    return stampBitmap
}