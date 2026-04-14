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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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

    // 动画状态
    var processedStampBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val dropAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }

    // --- 对焦 UI 状态 ---
    var tapOffset by remember { mutableStateOf<Offset?>(null) }
    val focusScale = remember { Animatable(1.5f) }
    val focusAlpha = remember { Animatable(0f) }

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
                    // 1. 获取点击位置并触发相机对焦+曝光调节
                    val factory = SurfaceOrientedMeteringPointFactory(size.width.toFloat(), size.height.toFloat())
                    val point = factory.createPoint(offset.x, offset.y)

                    // 同时启用对焦 (AF) 和 自动曝光 (AE)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS) // 3秒后恢复全自动
                        .build()

                    camera?.cameraControl?.startFocusAndMetering(action)

                    // 2. 触发视觉反馈动画
                    tapOffset = offset
                    scope.launch {
                        focusAlpha.snapTo(1f)
                        focusScale.snapTo(1.5f)
                        focusScale.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                        delay(500)
                        focusAlpha.animateTo(0f, tween(300))
                        tapOffset = null
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                    camera?.cameraControl?.setZoomRatio(currentZoom * zoom)
                }
            }
    ) {
        // 1. 绘制预览遮罩和邮票边框
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val rect = getRect(viewSize)

            // 背景遮罩
            clipPath(Path().apply { addRect(rect) }, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }

            // 邮票白边及齿孔绘制逻辑
            val borderOutset = 15f
            val frameRect = Rect(rect.left - borderOutset, rect.top - borderOutset, rect.right + borderOutset, rect.bottom + borderOutset)
            val holeRadius = 12f
            val spacing = 45f

            val stampPath = Path().apply { addRect(frameRect) }
            val holesPath = Path().apply {
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
                drawEdgeHoles(frameRect.left, frameRect.top, frameRect.right, frameRect.top)
                drawEdgeHoles(frameRect.left, frameRect.bottom, frameRect.right, frameRect.bottom)
                drawEdgeHoles(frameRect.left, frameRect.top, frameRect.left, frameRect.bottom)
                drawEdgeHoles(frameRect.right, frameRect.top, frameRect.right, frameRect.bottom)
            }

            val finalPath = Path.combine(PathOperation.Difference, stampPath, holesPath)
            clipPath(Path().apply { addRect(rect) }, clipOp = ClipOp.Difference) {
                drawPath(finalPath, Color.White)
            }

            // --- 绘制对焦框视觉反馈 ---
            tapOffset?.let { offset ->
                val size = 70.dp.toPx() * focusScale.value
                drawRect(
                    color = Color.White.copy(alpha = focusAlpha.value),
                    topLeft = Offset(offset.x - size / 2, offset.y - size / 2),
                    size = androidx.compose.ui.geometry.Size(size, size),
                    style = Stroke(width = 2.dp.toPx())
                )
                // 对焦中心点
                drawCircle(
                    color = Color.White.copy(alpha = focusAlpha.value),
                    radius = 4.dp.toPx(),
                    center = offset
                )
            }
        }

        // 2. 邮票掉落动画展示
        processedStampBitmap?.let { bitmap ->
            val rect = getRect(viewSize)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dropAnim.value * size.height
                        alpha = alphaAnim.value
                        rotationZ = dropAnim.value * 15f
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
                        modifier = Modifier.fillMaxSize().padding(10.dp)
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
                                val finalBitmap = processAndSaveStamp(
                                    bitmap, rotation, context,
                                    RectF(rect.left, rect.top, rect.right, rect.bottom),
                                    viewSize.width, viewSize.height, loc
                                )

                                withContext(Dispatchers.Main) {
                                    processedStampBitmap = finalBitmap
                                    isProcessing = false
                                    Toast.makeText(context, "集邮成功！", Toast.LENGTH_SHORT).show()

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