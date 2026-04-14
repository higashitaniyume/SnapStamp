package top.valency.snapstamp.ui.screens

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
import androidx.camera.core.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.valency.snapstamp.ui.components.StampShape
import top.valency.snapstamp.utils.processAndSaveStamp
import top.valency.snapstamp.utils.takePhotoWithPermission
import java.util.concurrent.TimeUnit

@Composable
fun CameraOverlay(imageCapture: ImageCapture, camera: Camera?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var isProcessing by remember { mutableStateOf(false) }

    // 动画状态：这里存储的是处理合成后的最终邮票图片
    var processedStampBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val dropAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }

    val getRect = { size: IntSize ->
        val s = size.width * 0.75f
        val l = (size.width - s) / 2
        val t = (size.height - s) / 2 - 100f
        Rect(l, t, l + s, t + s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { viewSize = it.size }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val factory = SurfaceOrientedMeteringPointFactory(size.width.toFloat(), size.height.toFloat())
                    val action = FocusMeteringAction.Builder(factory.createPoint(offset.x, offset.y))
                        .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    camera?.cameraControl?.startFocusAndMetering(action)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                    camera?.cameraControl?.setZoomRatio(currentZoom * zoom)
                }
            }
    ) {
        // 1. 绘制预览遮罩和正确的邮票白边
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val rect = getRect(viewSize)

            // A. 绘制背景遮罩
            clipPath(Path().apply { addRect(rect) }, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }

            // B. 绘制邮票齿孔白边 (修正逻辑：白边在内，齿孔切掉边缘)
            val borderOutset = 15f // 白边比取景框稍微大出一点点，用于容纳齿孔
            val frameRect = Rect(rect.left - borderOutset, rect.top - borderOutset, rect.right + borderOutset, rect.bottom + borderOutset)

            val holeRadius = 12f
            val spacing = 45f

            // 创建带齿孔的路径
            val stampPath = Path().apply {
                addRect(frameRect)
            }
            val holesPath = Path().apply {
                // 定义画孔逻辑：圆心要在白边的边缘上，这样才能切出半圆
                val drawEdgeHoles = { xStart: Float, yStart: Float, xEnd: Float, yEnd: Float ->
                    val isHor = yStart == yEnd
                    val len = if (isHor) xEnd - xStart else yEnd - yStart
                    val steps = (len / spacing).toInt()
                    for (i in 0..steps) {
                        val px = if (isHor) xStart + i * (len / steps) else xStart
                        val py = if (isHor) yStart else yStart + i * (len / steps)
                        addOval(Rect(px - holeRadius, py - holeRadius, px + holeRadius, py + holeRadius))
                    }
                }
                drawEdgeHoles(frameRect.left, frameRect.top, frameRect.right, frameRect.top) // 上
                drawEdgeHoles(frameRect.left, frameRect.bottom, frameRect.right, frameRect.bottom) // 下
                drawEdgeHoles(frameRect.left, frameRect.top, frameRect.left, frameRect.bottom) // 左
                drawEdgeHoles(frameRect.right, frameRect.top, frameRect.right, frameRect.bottom) // 右
            }

            // 用 Difference 操作减去圆孔，形成真正的邮票边缘
            val finalPath = Path.combine(PathOperation.Difference, stampPath, holesPath)

            // 绘制白边，但要挖掉取景框中间部分
            clipPath(Path().apply { addRect(rect) }, clipOp = ClipOp.Difference) {
                drawPath(finalPath, Color.White)
            }
        }

        // 2. 邮票掉落动画 (此时展示的是处理合成后的图片)
        processedStampBitmap?.let { bitmap ->
            val rect = getRect(viewSize)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dropAnim.value * size.height
                        alpha = alphaAnim.value
                        rotationZ = dropAnim.value * 15f // 增加点掉落旋转感
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = (rect.top / context.resources.displayMetrics.density).dp)
                        .size((rect.width / context.resources.displayMetrics.density).dp)
                        .clip(StampShape(10f, 30f)),
                    color = Color.White,
                    shadowElevation = 15.dp
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(10.dp) // 这里的Padding就是邮票内的白边
                    )
                }
            }
        }

        if (isProcessing) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)

        // 3. 拍照按钮
        Button(
            onClick = {
                if (isProcessing) return@Button
                isProcessing = true
                takePhotoWithPermission(context, fusedLocationClient) { loc ->
                    imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            val rotation = image.imageInfo.rotationDegrees
                            image.close()

                            scope.launch(Dispatchers.Default) {
                                val rect = getRect(viewSize)
                                // 第一步：调用你的工具类处理并保存，获取合成后的 Bitmap
                                // 假设 processAndSaveStamp 返回合成后的 Bitmap，如果不返回，请在内部生成
                                val finalBitmap = processAndSaveStamp(
                                    bitmap, rotation, context,
                                    RectF(rect.left, rect.top, rect.right, rect.bottom),
                                    viewSize.width, viewSize.height, loc
                                )

                                withContext(Dispatchers.Main) {
                                    // 第二步：处理完成后，将合成后的图片赋值给状态，触发动画
                                    processedStampBitmap = finalBitmap as Bitmap?
                                    isProcessing = false
                                    Toast.makeText(context, "集邮成功！", Toast.LENGTH_SHORT).show()

                                    // 第三步：执行掉落动画
                                    launch {
                                        dropAnim.animateTo(1.2f, tween(1000))
                                        processedStampBitmap = null
                                        dropAnim.snapTo(0f)
                                    }
                                    launch {
                                        delay(700)
                                        alphaAnim.animateTo(0f, tween(300))
                                        alphaAnim.snapTo(1f)
                                    }
                                }
                            }
                        }
                        override fun onError(exc: ImageCaptureException) { isProcessing = false }
                    })
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).size(80.dp),
            shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.White), enabled = !isProcessing
        ) { Box(modifier = Modifier.size(65.dp).border(4.dp, Color.LightGray, CircleShape)) }
    }
}