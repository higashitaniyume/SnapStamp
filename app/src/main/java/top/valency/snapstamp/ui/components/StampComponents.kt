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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.valency.snapstamp.model.StampModel
import java.io.File

class StampShape(private val holeRadius: Float = 15f, private val spacing: Float = 45f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rectPath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val holesPath = Path().apply {
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
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .graphicsLayer { shape = StampShape(holeRadius = 10f, spacing = 30f); clip = true },
        color = Color.White, shadowElevation = 6.dp
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            AsyncImage(model = stamp.file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun FlipStampCard(stamp: StampModel, displayFile: File? = null) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "")

    // 如果外部没传 displayFile，默认用 stamp.file
    val fileToShow = displayFile ?: stamp.file

    Surface(
        modifier = Modifier.size(320.dp).graphicsLayer {
            rotationY = rotation
            cameraDistance = 15 * density
            // 注意：StampShape 是你自定义的绘制形状，确保它在这里正确引用
            shape = StampShape(15f, 45f)
            clip = true
        }.clickable { flipped = !flipped },
        color = Color.White, shadowElevation = 12.dp
    ) {
        if (rotation <= 90f) {
            Box(Modifier.fillMaxSize().padding(20.dp)) {
                // 使用 AsyncImage 加载当前指定的文件
                AsyncImage(
                    model = fileToShow,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            // 背面保持不变
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SNAP STAMP", fontWeight = FontWeight.Bold, color = Color.Gray)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("日期: ${stamp.date}", fontSize = 12.sp, color = Color.Gray)
                    Text("设备: ${stamp.info}", fontSize = 12.sp, color = Color.Gray)
                    Text("坐标: ${stamp.location}", fontSize = 12.sp, color = Color.Gray)

                    if (stamp.remark.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("备注: ${stamp.remark}", fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    }

                    Spacer(Modifier.weight(1f))
                    androidx.compose.foundation.Canvas(Modifier.size(60.dp)) {
                        drawCircle(Color.LightGray, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                    Text("*已支付邮资*", fontSize = 10.sp, color = Color.LightGray)
                }
            }
        }
    }
}