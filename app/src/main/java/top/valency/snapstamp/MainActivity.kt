package top.valency.snapstamp

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.valency.snapstamp.ui.theme.SnapStampTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 别名简化
typealias ComposeColor = Color

// --- 1. 邮票锯齿形状 ---
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
            if (isHorizontal) {
                path.addOval(Rect(center - holeRadius, y - holeRadius, center + holeRadius, y + holeRadius))
            } else {
                path.addOval(Rect(x - holeRadius, center - holeRadius, x + holeRadius, center + holeRadius))
            }
        }
    }
}

data class StampModel(val fileName: String, val file: File, val date: String, val info: String, val location: String)

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Camera : Screen("camera", "生成", Icons.Default.PhotoCamera)
    object Library : Screen("library", "库", Icons.Default.Collections)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SnapStampTheme { MainScreen() } }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()

    // 全局相机状态，保证切换页面不关闭相机
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCameraPermission = it[Manifest.permission.CAMERA] ?: false
    }
    LaunchedEffect(Unit) { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // --- 修改：给整个应用增加默认背景色 ---
    ) {
        // --- 1. 全局背景：相机预览 (始终在最底层运行) ---
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding() // --- 修改：加上状态栏间距，使相机画面不伸展进入刘海（状态栏）部分 ---
            ) { view ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    } catch (e: Exception) { Log.e("Camera", "Binding Error", e) }
                }, ContextCompat.getMainExecutor(context))
            }
        }

        // --- 2. 导航视图 ---
        Scaffold(
            containerColor = Color.Transparent, // Scaffold 透明，由子页面决定是否遮挡相机
            bottomBar = { BottomNavigationBar(navController) }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                NavigationGraph(navController, imageCapture)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(Screen.Camera, Screen.Library)
    // 恢复正常配色，不再透明
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, null) },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId); launchSingleTop = true
                    }
                }
            )
        }
    }
}

@Composable
fun NavigationGraph(navController: NavHostController, imageCapture: ImageCapture) {
    NavHost(navController, startDestination = Screen.Camera.route) {
        // 生成页面：透明背景，显示相机并叠加遮罩
        composable(Screen.Camera.route) { CameraOverlay(imageCapture) }
        // 库页面：实色背景，完全遮挡相机
        composable(Screen.Library.route) { LibraryScreen() }
    }
}

@Composable
fun CameraOverlay(imageCapture: ImageCapture) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var isProcessing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { viewSize = it.size }) {
        // 镂空遮罩层
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val stampSize = size.width * 0.75f
            val left = (size.width - stampSize) / 2
            val top = (size.height - stampSize) / 2 - 100f
            clipPath(Path().apply { addRect(Rect(left, top, left + stampSize, top + stampSize)) }, clipOp = ClipOp.Difference) {
                drawRect(color = Color.Black.copy(alpha = 0.6f))
            }
        }

        if (isProcessing) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

        Button(
            onClick = {
                isProcessing = true
                takePhotoWithPermission(context, fusedLocationClient) { loc ->
                    imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            val rotation = image.imageInfo.rotationDegrees
                            image.close()
                            scope.launch(Dispatchers.Default) {
                                val stampSizePx = viewSize.width * 0.75f
                                val left = (viewSize.width - stampSizePx) / 2
                                val top = (viewSize.height - stampSizePx) / 2 - 100f
                                processAndSaveStamp(bitmap, rotation, context, RectF(left, top, left + stampSizePx, top + stampSizePx), viewSize.width, viewSize.height, loc)
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    Toast.makeText(context, "集邮成功！", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        override fun onError(exc: ImageCaptureException) { isProcessing = false }
                    })
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).size(75.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            enabled = !isProcessing
        ) { Box(modifier = Modifier.size(60.dp).border(3.dp, Color.Gray, CircleShape)) }
    }
}

private fun takePhotoWithPermission(
    context: Context,
    client: FusedLocationProviderClient,
    onResult: (android.location.Location?) -> Unit
) {
    val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
        try {
            client.lastLocation.addOnCompleteListener { task ->
                onResult(if (task.isSuccessful) task.result else null)
            }
        } catch (e: SecurityException) {
            Log.e("Permission", "SecurityException: ${e.message}")
            onResult(null)
        }
    } else {
        onResult(null)
    }
}

private suspend fun processAndSaveStamp(
    original: Bitmap, rotation: Int, context: Context,
    screenRect: RectF, viewW: Int, viewH: Int, loc: android.location.Location?
) = withContext(Dispatchers.Default) {
    val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    val scaleX = rotated.width.toFloat() / viewW
    val scaleY = rotated.height.toFloat() / viewH
    val cropLeft = (screenRect.left * scaleX).toInt().coerceAtLeast(0)
    val cropTop = (screenRect.top * scaleY).toInt().coerceAtLeast(0)
    val cropSize = (screenRect.width() * scaleX).toInt()
        .coerceAtMost(rotated.width - cropLeft)
        .coerceAtMost(rotated.height - cropTop)

    val cropped = Bitmap.createBitmap(rotated, cropLeft, cropTop, cropSize, cropSize)
    // 修正拼写错误 HHmmss
    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File(context.filesDir, "STAMP_$timeStr.jpg")

    try {
        FileOutputStream(file).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        val exif = ExifInterface(file.absolutePath)
        loc?.let { exif.setGpsInfo(it) }
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        exif.setAttribute(ExifInterface.TAG_DATETIME, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date()))
        exif.saveAttributes()

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { u -> context.contentResolver.openOutputStream(u)?.use { it.write(file.readBytes()) } }
    } catch (e: Exception) { Log.e("Save", "Fail", e) }
}

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stampList by remember { mutableStateOf<List<StampModel>>(emptyList()) }
    var selectedStamp by remember { mutableStateOf<StampModel?>(null) }

    fun load() {
        // 使用 Dispatchers.IO 解决在非阻塞上下文中进行阻塞调用的问题
        scope.launch(Dispatchers.IO) {
            val files = context.filesDir.listFiles { f -> f.name.startsWith("STAMP_") }?.toList() ?: emptyList()
            val mapped = files.sortedByDescending { it.lastModified() }.map { f ->
                val exif = ExifInterface(f.absolutePath)
                StampModel(
                    fileName = f.name,
                    file = f,
                    date = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "未知日期",
                    info = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "未知设备",
                    location = exif.latLong?.let { "${String.format(Locale.getDefault(), "%.2f", it[0])}, ${String.format(Locale.getDefault(), "%.2f", it[1])}" } ?: "无位置信息"
                )
            }
            withContext(Dispatchers.Main) { stampList = mapped }
        }
    }
    LaunchedEffect(Unit) { load() }

    // 库页面使用实色背景，完全遮盖底下的相机
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Text("我的集邮册", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(stampList) { stamp ->
                        StampItem(stamp, onClick = { selectedStamp = stamp }, onLongClick = {
                            scope.launch(Dispatchers.IO) {
                                stamp.file.delete()
                                context.contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(stamp.fileName))
                                load()
                            }
                        })
                    }
                }
            }

            // 详情大图弹出层
            AnimatedVisibility(selectedStamp != null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { selectedStamp = null }, Alignment.Center) {
                    selectedStamp?.let { FlipStampCard(it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StampItem(stamp: StampModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .graphicsLayer {
                shape = StampShape(holeRadius = 10f, spacing = 30f)
                clip = true
            },
        color = Color.White,
        shadowElevation = 6.dp
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            AsyncImage(model = stamp.file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun FlipStampCard(stamp: StampModel) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "")

    Surface(
        modifier = Modifier.size(320.dp).graphicsLayer {
            rotationY = rotation
            cameraDistance = 15 * density
            shape = StampShape(15f, 45f)
            clip = true
        }.clickable { flipped = !flipped },
        color = Color.White,
        shadowElevation = 12.dp
    ) {
        if (rotation <= 90f) {
            Box(Modifier.fillMaxSize().padding(20.dp)) {
                AsyncImage(model = stamp.file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SNAP STAMP", fontWeight = FontWeight.Bold, color = Color.Gray)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("日期: ${stamp.date}", fontSize = 12.sp, color = Color.Gray)
                    Text("设备: ${stamp.info}", fontSize = 12.sp, color = Color.Gray)
                    Text("坐标: ${stamp.location}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(40.dp))
                    ComposeCanvas(Modifier.size(60.dp)) {
                        drawCircle(Color.LightGray, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                    Text("*已支付邮资*", fontSize = 10.sp, color = Color.LightGray)
                }
            }
        }
    }
}