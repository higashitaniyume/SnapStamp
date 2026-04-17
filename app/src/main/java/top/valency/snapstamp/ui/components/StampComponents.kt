package top.valency.snapstamp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.valency.snapstamp.R
import top.valency.snapstamp.model.StampModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

// 1. 现代化动态比例锯齿：使用基于宽度的动态比例，完美适配各种屏幕 DPI
class StampShape(
    private val radiusRatio: Float = 0.012f,  // 孔洞半径占宽度的比例 (1.2%)
    private val spacingRatio: Float = 0.038f  // 孔洞间距占宽度的比例 (3.8%)
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val holeRadius = size.width * radiusRatio
        val spacing = size.width * spacingRatio

        val rectPath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val holesPath = Path().apply {
            drawHoles(this, size.width, spacing, holeRadius, isHorizontal = true, y = 0f)
            drawHoles(this, size.width, spacing, holeRadius, isHorizontal = true, y = size.height)
            drawHoles(this, size.height, spacing, holeRadius, isHorizontal = false, x = 0f)
            drawHoles(this, size.height, spacing, holeRadius, isHorizontal = false, x = size.width)
        }
        val finalPath = Path.combine(PathOperation.Difference, rectPath, holesPath)
        return Outline.Generic(finalPath)
    }

    private fun drawHoles(path: Path, length: Float, spacing: Float, holeRadius: Float, isHorizontal: Boolean, x: Float = 0f, y: Float = 0f) {
        val stepCount = (length / spacing).toInt().coerceAtLeast(1)
        val actualSpacing = length / stepCount
        for (i in 0..stepCount) {
            val center = i * actualSpacing
            if (isHorizontal) path.addOval(Rect(center - holeRadius, y - holeRadius, center + holeRadius, y + holeRadius))
            else path.addOval(Rect(x - holeRadius, center - holeRadius, x + holeRadius, center + holeRadius))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StampItem(stamp: StampModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    val formattedDate = remember(stamp.date) {
        try {
            val inputFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.ENGLISH)
            val dateObj = inputFormat.parse(stamp.date)
            dateObj?.let { outputFormat.format(it).uppercase() } ?: stamp.date
        } catch (e: Exception) {
            stamp.date
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // 列表模式下稍微加大间距比例，视觉上更清晰
            .graphicsLayer { shape = StampShape(0.015f, 0.045f); clip = true },
        color = Color(0xFFFCFCFA), // 现代纸白色
        shadowElevation = 4.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            // 2. 图片区域：增加极细的微描边(Border)，增加照片贴在纸上的物理厚度感
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = 0.dp)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.08f), RectangleShape)
                    .clip(RectangleShape)
            ) {
                AsyncImage(
                    model = stamp.file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, // 列表中推荐 Crop 以保持网格整洁
                    alignment = Alignment.Center
                )
            }

            // 3. 信息区域：极致对称排版
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.6f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 9.sp,
                        color = Color(0xFF202020), // 接近纯黑的深灰
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (stamp.location.isNotBlank()) {
                        Text(
                            text = stamp.location,
                            fontSize = 7.sp,
                            color = Color(0xFF888888),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (stamp.info.isNotBlank()) {
                    Text(
                        text = stamp.info,
                        fontSize = 9.sp,
                        color = Color(0xFF3C3C3C),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.4f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FlipStampCard(stamp: StampModel, displayFile: File? = null) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "")

    val fileToShow = displayFile ?: stamp.file

    val formattedDate = remember(stamp.date) {
        try {
            val inputFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.ENGLISH)
            val dateObj = inputFormat.parse(stamp.date)
            dateObj?.let { outputFormat.format(it).uppercase() } ?: stamp.date
        } catch (e: Exception) {
            stamp.date
        }
    }

    Surface(
        modifier = Modifier
            .size(width = 300.dp, height = 400.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 15 * density
                shape = StampShape(0.012f, 0.038f) // 更精致细密的孔洞
                clip = true
            }
            .clickable { flipped = !flipped },
        color = Color(0xFFFCFCFA),
        shadowElevation = 16.dp
    ) {
        if (rotation <= 90f) {
            // ================= 【正面】：画廊/明信片风格 =================
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
                        .border(0.5.dp, Color.Black.copy(alpha = 0.1f), RectangleShape)
                        .clip(RectangleShape)
                ) {
                    AsyncImage(
                        model = fileToShow,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        // 详情页可以改用 Fit 保留全图，或保持 Crop。此处用 Crop 铺满框内
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.6f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formattedDate,
                            fontSize = 11.sp,
                            color = Color(0xFF202020),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        if (stamp.location.isNotBlank()) {
                            Text(
                                text = stamp.location,
                                fontSize = 9.sp,
                                color = Color(0xFF888888),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (stamp.info.isNotBlank()) {
                        Text(
                            text = stamp.info,
                            fontSize = 11.sp,
                            color = Color(0xFF3C3C3C),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.4f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            // ================= 【背面】：复古证书/印戳排版风格 =================
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 顶部 Title
                    Text(
                        text = stringResource(R.string.stamp_back_title).uppercase(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color(0xFF333333),
                        fontSize = 14.sp
                    )

                    HorizontalDivider(
                        Modifier.padding(vertical = 16.dp),
                        thickness = 0.5.dp,
                        color = Color.Black.copy(alpha = 0.1f)
                    )

                    // 左对齐的元数据列表（更像相片背后的备注记录）
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BackCardInfoRow("DATE", stamp.date)
                        BackCardInfoRow("DEVICE", stamp.info)
                        BackCardInfoRow("PLACE", stamp.location)
                    }

                    if (stamp.remark.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stamp.remark, // 直接展示 Remark 文字本身
                            fontSize = 13.sp,
                            color = Color(0xFF444444),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // 底部优雅的邮戳设计 (Postmark)
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(56.dp)) {
                            // 画一个极细的邮戳圆圈
                            drawCircle(
                                color = Color(0xFFC0C0C0),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        Text(
                            text = stringResource(R.string.stamp_back_postage).uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF999999),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

// 背面信息排版辅助组件 (左标题 - 右内容)
@Composable
private fun BackCardInfoRow(label: String, value: String) {
    if (value.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF999999),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(0.3f)
            )
            Text(
                text = value,
                fontSize = 11.sp,
                color = Color(0xFF555555),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.7f)
            )
        }
    }
}