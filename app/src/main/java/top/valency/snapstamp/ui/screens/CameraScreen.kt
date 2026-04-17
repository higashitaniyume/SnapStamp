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
import androidx.lifecycle.Observer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.valency.snapstamp.data.AppSettings
import top.valency.snapstamp.data.SettingsStore
import top.valency.snapstamp.R
import top.valency.snapstamp.ui.components.StampShape
import java.util.concurrent.TimeUnit

@Composable
fun CameraOverlay(imageCapture: ImageCapture, camera: Camera?) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val settingsStore = remember(context) { SettingsStore(context) }
    val settings by settingsStore.settingsFlow.collectAsState(initial = AppSettings())

    val viewModel: CameraViewModel = viewModel()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processedStampBitmap by viewModel.processedStampBitmap.collectAsState()

    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // --- 相机控制状态 (变焦和曝光) ---
    var zoomValue by remember { mutableFloatStateOf(0f) }
    var exposureValue by remember { mutableFloatStateOf(0f) }
    var exposureRange by remember { mutableStateOf(-10f..10f) }

    DisposableEffect(camera) {
        val zoomObserver = Observer<ZoomState> { state ->
            zoomValue = state.linearZoom
        }
        camera?.cameraInfo?.zoomState?.observeForever(zoomObserver)

        camera?.cameraInfo?.exposureState?.let { expState ->
            val range = expState.exposureCompensationRange
            if (range.lower < range.upper) {
                exposureRange = range.lower.toFloat()..range.upper.toFloat()
            } else {
                exposureRange = 0f..0f
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
    val dropAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }

    LaunchedEffect(processedStampBitmap) {
        if (processedStampBitmap != null) {
            dropAnim.snapTo(0f)
            alphaAnim.snapTo(1f)
            
            launch {
                dropAnim.animateTo(1.2f, tween(1200, easing = FastOutSlowInEasing))
                viewModel.clearProcessedBitmap()
            }
            
            launch {
                delay(800)
                alphaAnim.animateTo(0f, tween(400))
            }
        }
    }

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
                    val factory = SurfaceOrientedMeteringPointFactory(size.width.toFloat(), size.height.toFloat())
                    val point = factory.createPoint(offset.x, offset.y)

                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(settings.focusAutoResetSec.toLong(), TimeUnit.SECONDS)
                        .build()

                    camera?.cameraControl?.startFocusAndMetering(action)

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
        // 1. 绘制预览遮罩
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val rect = getRect(viewSize)
            clipPath(Path().apply { addRect(rect) }, clipOp = ClipOp.Difference) {
                drawRect(Color.Black)
            }

            tapOffset?.let { offset ->
                val size = 70.dp.toPx() * focusScale.value
                drawRect(
                    color = Color.White.copy(alpha = focusAlpha.value),
                    topLeft = Offset(offset.x - size / 2, offset.y - size / 2),
                    size = androidx.compose.ui.geometry.Size(size, size),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = focusAlpha.value),
                    radius = 4.dp.toPx(),
                    center = offset
                )
            }
        }

        // 2. 附加UI
        if (viewSize != IntSize.Zero && camera != null) {
            val rect = getRect(viewSize)
            val density = LocalDensity.current
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

            val sliderLengthDp = with(density) { rect.height.toDp() }
            val sliderWidthDp = 40.dp
            val sliderCenterY = with(density) { (rect.top + rect.height / 2).toDp() }

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
                        .offset(x = leftCenterX - sliderLengthDp / 2, y = sliderCenterY - sliderWidthDp / 2)
                        .width(sliderLengthDp)
                        .height(sliderWidthDp)
                        .rotate(-90f)
                )
            }

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
                    .offset(x = rightCenterX - sliderLengthDp / 2, y = sliderCenterY - sliderWidthDp / 2)
                    .width(sliderLengthDp)
                    .height(sliderWidthDp)
                    .rotate(-90f)
            )
        }

        // 3. 邮票掉落动画展示
        processedStampBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                val rect = getRect(viewSize)
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val remainingDist = size.height - rect.top
                            translationY = dropAnim.value * remainingDist
                            alpha = alphaAnim.value
                            rotationZ = dropAnim.value * 15f
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(top = with(density) { rect.top.toDp() })
                            .size(
                                width = with(density) { rect.width.toDp() },
                                height = with(density) { (rect.width * 4f / 3f).toDp() }
                            )
                            .graphicsLayer {
                                shape = StampShape(0.009f, 0.032f)
                                clip = true
                            },
                        color = Color(0xFFFCFCFA),
                        shadowElevation = 15.dp
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
                if (settings.shutterFeedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                val rect = getRect(viewSize)
                viewModel.takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    fusedLocationClient = fusedLocationClient,
                    settings = settings,
                    viewWidth = viewSize.width,
                    viewHeight = viewSize.height,
                    screenRect = RectF(rect.left, rect.top, rect.right, rect.bottom),
                    onSuccess = { Toast.makeText(context, context.getString(R.string.camerascr_stamp_success), Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                )
            }
        )
    }
}

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
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 2,
                style = Stroke(width = 4.dp.toPx())
            )
        }

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
