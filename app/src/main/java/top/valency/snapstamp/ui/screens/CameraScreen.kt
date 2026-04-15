package top.valency.snapstamp.ui.screens

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
import androidx.camera.core.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.valency.snapstamp.data.AppSettings
import top.valency.snapstamp.data.SettingsStore
import top.valency.snapstamp.R
import top.valency.snapstamp.ui.components.StampShape
import top.valency.snapstamp.utils.processAndSaveStamp
import top.valency.snapstamp.utils.takePhotoWithPermission
import java.util.concurrent.TimeUnit

@Composable
fun CameraOverlay(imageCapture: ImageCapture, camera: Camera?) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val settingsStore = remember(context) { SettingsStore(context) }
    val settings by settingsStore.settingsFlow.collectAsState(initial = AppSettings())

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var isProcessing by remember { mutableStateOf(false) }

    // --- 相机控制状态 (变焦和曝光) ---
    var zoomValue by remember { mutableFloatStateOf(0f) }
    var exposureValue by remember { mutableFloatStateOf(0f) }
    var exposureRange by remember { mutableStateOf(-10f..10f) }

    DisposableEffect(camera) {
        // 监听 CameraX 底层的变焦变化（用于双指缩放同步 Slider）
        val zoomObserver = Observer<ZoomState> { state ->
            zoomValue = state.linearZoom
        }
        camera?.cameraInfo?.zoomState?.observeForever(zoomObserver)

        // 初始化曝光状态
        camera?.cameraInfo?.exposureState?.let { expState ->
            val range = expState.exposureCompensationRange
            if (range.lower < range.upper) {
                exposureRange = range.lower.toFloat()..range.upper.toFloat()
            } else {
                exposureRange = 0f..0f // 设备不支持调整曝光
            }
            exposureValue = expState.exposureCompensationIndex.toFloat()
        }

        onDispose {
            camera?.cameraInfo?.zoomState?.removeObserver(zoomObserver)
        }
    }

    LaunchedEffect(camera, settings.defaultZoom, settings.defaultExposure) {
        camera?.cameraControl?.setLinearZoom(settings.defaultZoom.coerceIn(0f, 1f))
        camera?.cameraControl?.setExposureCompensationIndex(settings.defaultExposure)
    }

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
                        .setAutoCancelDuration(settings.focusAutoResetSec.toLong(), TimeUnit.SECONDS)
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
                    // 双指变焦时触发 (Slider 会通过 Observer 自动更新)
                    val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                    camera?.cameraControl?.setZoomRatio(currentZoom * zoom)
                }
            }
    ) {
        // 1. 绘制预览遮罩
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val rect = getRect(viewSize)

            // 背景遮罩改为完全不透明的纯黑色，挖空中间预览区
            clipPath(Path().apply { addRect(rect) }, clipOp = ClipOp.Difference) {
                drawRect(Color.Black)
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

        // 2. 附加UI (指导语 及 两侧滑动条)
        if (viewSize != IntSize.Zero && camera != null) {
            val rect = getRect(viewSize)
            val density = LocalDensity.current

            // 文本在 Y 轴的偏移量，让它悬浮在取景框上方
            val textOffsetY = with(density) { rect.top.toDp() - 70.dp }

            if (settings.framingGuide) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = textOffsetY),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.camerascr_preview_lable_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.camerascr_preview_lable_text),
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }

            // ====== 新增：两侧滑动条控制区 ======
            val sliderLengthDp = with(density) { rect.height.toDp() }
            val sliderWidthDp = 40.dp
            val sliderCenterY = with(density) { (rect.top + rect.height / 2).toDp() }

            // 左侧：曝光调整
            if (exposureRange.start < exposureRange.endInclusive) {
                val leftCenterX = with(density) { (rect.left / 2).toDp() }
                Text(
                    text = stringResource(R.string.camerascr_exposure),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = leftCenterX - 12.dp, y = with(density) { rect.top.toDp() } - 30.dp)
                )
                Slider(
                    value = exposureValue,
                    valueRange = exposureRange,
                    onValueChange = { v ->
                        exposureValue = v
                        camera.cameraControl.setExposureCompensationIndex(v.toInt())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.8f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = leftCenterX - sliderLengthDp / 2, // 确保横置时中心点对齐
                            y = sliderCenterY - sliderWidthDp / 2
                        )
                        .width(sliderLengthDp)
                        .height(sliderWidthDp)
                        .rotate(-90f) // 旋转实现垂直滑动条
                )
            }

            // 右侧：变焦缩放
            val rightCenterX = with(density) { (rect.right + (viewSize.width - rect.right) / 2).toDp() }
            Text(
                text = stringResource(R.string.camerascr_zoom),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = rightCenterX - 12.dp, y = with(density) { rect.top.toDp() } - 30.dp)
            )
            Slider(
                value = zoomValue,
                valueRange = 0f..1f,
                onValueChange = { v ->
                    zoomValue = v
                    camera.cameraControl.setLinearZoom(v)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.8f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = rightCenterX - sliderLengthDp / 2,
                        y = sliderCenterY - sliderWidthDp / 2
                    )
                    .width(sliderLengthDp)
                    .height(sliderWidthDp)
                    .rotate(-90f)
            )
        }

        // 3. 邮票掉落动画展示
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }
            }
        }

        // 4. 拍照按钮
        ModernShutterButton(
            isProcessing = isProcessing,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            onClick = {
                if (isProcessing) return@ModernShutterButton
                isProcessing = true
                if (settings.shutterFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                takePhotoWithPermission(context, fusedLocationClient, settings.writeLocationExif) { loc ->
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
                                    viewSize.width, viewSize.height, loc,
                                    jpegQuality = settings.jpegQuality,
                                    saveInternalCopy = settings.keepInternalCopy && settings.allowBackup,
                                    autoSaveToAlbum = settings.autoSaveAfterShot && settings.saveToSystemAlbum,
                                    borderStrength = settings.borderThickness,
                                    borderClassicStyle = settings.borderClassicStyle,
                                    infoVisibleOverlay = settings.infoVisibleOverlay
                                )

                                withContext(Dispatchers.Main) {
                                    processedStampBitmap = finalBitmap
                                    isProcessing = false
                                    Toast.makeText(context, context.getString(R.string.camerascr_stamp_success), Toast.LENGTH_SHORT).show()

                                    if (settings.dropAnimation) {
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
                                    } else {
                                        processedStampBitmap = null
                                    }
                                }
                            }
                        }
                        override fun onError(exc: ImageCaptureException) { isProcessing = false }
                    })
                }
            }
        )
    }
}

// ==========================================
// 现代化快门按钮组件
// ==========================================
@Composable
fun ModernShutterButton(
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button_scale"
    )

    val innerCircleSize by animateDpAsState(
        targetValue = if (isProcessing) 42.dp else 66.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "inner_circle_size"
    )

    Box(
        modifier = modifier
            .size(84.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isProcessing,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 外层细边框白环
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 2,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // 内层实体圆
        Box(
            modifier = Modifier
                .size(innerCircleSize)
                .clip(CircleShape)
                .background(if (isProcessing) Color.White.copy(alpha = 0.8f) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = Color.Black,
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}