package top.valency.snapstamp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.valency.snapstamp.R
import top.valency.snapstamp.model.StampModel
import java.io.File

class StampShape(private val holeRadius: Float = 15f, private val spacing: Float = 45f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rectPath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val holesPath = Path().apply {
            // 这个逻辑已经可以完美适应长方形，无需修改
            drawHoles(this, size.width, isHorizontal = true, y = 0f)
            drawHoles(this, size.width, isHorizontal = true, y = size.height)
            drawHoles(this, size.height, isHorizontal = false, x = 0f)
            drawHoles(this, size.height, isHorizontal = false, x = size.width)
        }
        val finalPath = Path.combine(PathOperation.Difference, rectPath, holesPath)
        return Outline.Generic(finalPath)
    }
    private fun drawHoles(path: Path, length: Float, isHorizontal: Boolean, x: Float = 0f, y: Float = 0f) {
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f) // 【修改】3:4 标准竖直长宽比
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .graphicsLayer { shape = StampShape(holeRadius = 10f, spacing = 30f); clip = true },
        color = Color.White, shadowElevation = 6.dp
    ) {
        // 【修改】改为 Column 排版，上方放图，下方放日期
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = stamp.file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit // 【修改】保持比例居中
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stamp.date,
                fontSize = 12.sp,
                color = Color.DarkGray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start) // 【修改】日期靠左
            )
        }
    }
}

@Composable
fun FlipStampCard(stamp: StampModel, displayFile: File? = null) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "")

    val fileToShow = displayFile ?: stamp.file

    Surface(
        // 【修改】固定尺寸改为 300x400 的长方形
        modifier = Modifier.size(width = 300.dp, height = 400.dp).graphicsLayer {
            rotationY = rotation
            cameraDistance = 15 * density
            shape = StampShape(15f, 45f)
            clip = true
        }.clickable { flipped = !flipped },
        color = Color.White, shadowElevation = 12.dp
    ) {
        if (rotation <= 90f) {
            // 【修改】正面改为上方图、下方日期的布局
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AsyncImage(
                        model = fileToShow,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit // 保持比例居中
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stamp.date,
                    fontSize = 16.sp,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start) // 日期靠左
                )
            }
        } else {
            // 背面保持不变
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.stamp_back_title), fontWeight = FontWeight.Bold, color = Color.Gray)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(stringResource(R.string.stamp_back_date, stamp.date), fontSize = 12.sp, color = Color.Gray)
                    Text(stringResource(R.string.stamp_back_device, stamp.info), fontSize = 12.sp, color = Color.Gray)
                    Text(stringResource(R.string.stamp_back_location, stamp.location), fontSize = 12.sp, color = Color.Gray)

                    if (stamp.remark.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.stamp_back_remark, stamp.remark),
                            fontSize = 14.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    androidx.compose.foundation.Canvas(Modifier.size(60.dp)) {
                        drawCircle(Color.LightGray, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                    Text(stringResource(R.string.stamp_back_postage), fontSize = 10.sp, color = Color.LightGray)
                }
            }
        }
    }
}