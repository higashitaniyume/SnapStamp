package top.valency.snapstamp

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

// 绘制并生成带有锯齿孔洞的白边邮票位图
fun createStampBitmap(cropped: Bitmap): Bitmap {
    val cropSize = cropped.width
    val padding = cropSize * 0.05f
    val finalSize = cropSize + (padding * 2).toInt()
    val stampBitmap = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(stampBitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, finalSize.toFloat(), finalSize.toFloat(), paint)
    canvas.drawBitmap(cropped, padding, padding, null)

    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    val holeRadius = finalSize * 0.015f
    val spacing = finalSize * 0.045f
    val stepCount = (finalSize / spacing).toInt().coerceAtLeast(1)
    val actualSpacing = finalSize.toFloat() / stepCount

    for (i in 0..stepCount) {
        val center = i * actualSpacing
        canvas.drawCircle(center, 0f, holeRadius, paint)
        canvas.drawCircle(center, finalSize.toFloat(), holeRadius, paint)
        canvas.drawCircle(0f, center, holeRadius, paint)
        canvas.drawCircle(finalSize.toFloat(), center, holeRadius, paint)
    }
    return stampBitmap
}

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

data class StampModel(val fileName: String, val file: File, val date: String, val info: String, val location: String, val remark: String)

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

    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCameraPermission = it[Manifest.permission.CAMERA] ?: false
    }
    LaunchedEffect(Unit) { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (hasCameraPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().statusBarsPadding()) { view ->
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
        Scaffold(containerColor = Color.Transparent, bottomBar = { BottomNavigationBar(navController) }) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) { NavigationGraph(navController, imageCapture) }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(Screen.Camera, Screen.Library)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, null) },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) { popUpTo(navController.graph.startDestinationId); launchSingleTop = true }
                }
            )
        }
    }
}

@Composable
fun NavigationGraph(navController: NavHostController, imageCapture: ImageCapture) {
    NavHost(navController, startDestination = Screen.Camera.route) {
        composable(Screen.Camera.route) { CameraOverlay(imageCapture) }
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
            shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.White), enabled = !isProcessing
        ) { Box(modifier = Modifier.size(60.dp).border(3.dp, Color.Gray, CircleShape)) }
    }
}

private fun takePhotoWithPermission(context: Context, client: FusedLocationProviderClient, onResult: (android.location.Location?) -> Unit) {
    val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
        try { client.lastLocation.addOnCompleteListener { task -> onResult(if (task.isSuccessful) task.result else null) } }
        catch (e: SecurityException) { onResult(null) }
    } else { onResult(null) }
}

private suspend fun processAndSaveStamp(
    original: Bitmap, rotation: Int, context: Context,
    screenRect: RectF, viewW: Int, viewH: Int, loc: android.location.Location?
) = withContext(Dispatchers.Default) {
    val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

    val scale = maxOf(viewW.toFloat() / rotated.width, viewH.toFloat() / rotated.height)
    val dx = (viewW - rotated.width * scale) / 2f
    val dy = (viewH - rotated.height * scale) / 2f

    val cropLeft = ((screenRect.left - dx) / scale).toInt().coerceAtLeast(0)
    val cropTop = ((screenRect.top - dy) / scale).toInt().coerceAtLeast(0)
    val cropSize = (screenRect.width() / scale).toInt()
        .coerceAtMost(rotated.width - cropLeft)
        .coerceAtMost(rotated.height - cropTop)

    val cropped = Bitmap.createBitmap(rotated, cropLeft, cropTop, cropSize, cropSize)

    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val baseFileName = "STAMP_$timeStr"

    try {
        // 保存App内部无边框原图 (.jpg)
        val internalFile = File(context.filesDir, "$baseFileName.jpg")
        FileOutputStream(internalFile).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 95, out) }

        val exif = ExifInterface(internalFile.absolutePath)
        loc?.let { exif.setGpsInfo(it) }
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        exif.setAttribute(ExifInterface.TAG_DATETIME, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date()))
        exif.saveAttributes()

        // 绘制带边框的邮票相册版 (.png)
        val stampBitmap = createStampBitmap(cropped)
        val tempExportFile = File(context.cacheDir, "$baseFileName.png")
        FileOutputStream(tempExportFile).use { out -> stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

        copyExif(internalFile.absolutePath, tempExportFile.absolutePath)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, tempExportFile.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { u -> context.contentResolver.openOutputStream(u)?.use { it.write(tempExportFile.readBytes()) } }
        tempExportFile.delete()

    } catch (e: Exception) { Log.e("Save", "Fail", e) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stampList by remember { mutableStateOf<List<StampModel>>(emptyList()) }
    var selectedStamp by remember { mutableStateOf<StampModel?>(null) }

    var stampForOptions by remember { mutableStateOf<StampModel?>(null) }
    var showRemarkDialog by remember { mutableStateOf<StampModel?>(null) }
    var remarkText by remember { mutableStateOf("") }

    fun load() {
        scope.launch(Dispatchers.IO) {
            val files = context.filesDir.listFiles { f -> f.name.startsWith("STAMP_") && f.name.endsWith(".jpg") }?.toList() ?: emptyList()
            val mapped = files.sortedByDescending { it.lastModified() }.map { f ->
                val exif = ExifInterface(f.absolutePath)
                StampModel(
                    fileName = f.name,
                    file = f,
                    date = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "未知日期",
                    info = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "未知设备",
                    location = exif.latLong?.let { "${String.format(Locale.getDefault(), "%.2f", it[0])}, ${String.format(Locale.getDefault(), "%.2f", it[1])}" } ?: "无位置信息",
                    remark = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { decodeRemark(it) } ?: ""
                )
            }
            withContext(Dispatchers.Main) { stampList = mapped }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun shareStamp(fileName: String) {
        val pngName = fileName.replace(".jpg", ".png")
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(pngName), null
        )
        var uri: android.net.Uri? = null
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        if (uri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享邮票"))
        } else { Toast.makeText(context, "未在相册中找到该邮票", Toast.LENGTH_SHORT).show() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                        StampItem(stamp, onClick = { selectedStamp = stamp }, onLongClick = { stampForOptions = stamp })
                    }
                }
            }

            AnimatedVisibility(selectedStamp != null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { selectedStamp = null }, Alignment.Center) {
                    selectedStamp?.let { FlipStampCard(it) }
                }
            }
        }
    }

    if (stampForOptions != null) {
        ModalBottomSheet(onDismissRequest = { stampForOptions = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("分享相册原图") }, leadingContent = { Icon(Icons.Default.Share, null) },
                    modifier = Modifier.clickable { shareStamp(stampForOptions!!.fileName); stampForOptions = null }
                )
                // --- 新增选项 1：保存（重制为带白边） ---
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("保存邮票 (带边框)") }, leadingContent = { Icon(Icons.Default.Save, null) },
                    modifier = Modifier.clickable {
                        val stamp = stampForOptions!!
                        scope.launch(Dispatchers.IO) {
                            val originalBitmap = BitmapFactory.decodeFile(stamp.file.absolutePath)
                            val stampBitmap = createStampBitmap(originalBitmap)
                            val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            val tempExportFile = File(context.cacheDir, "STAMP_MANUAL_$timeStr.png")

                            FileOutputStream(tempExportFile).use { out -> stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                            copyExif(stamp.file.absolutePath, tempExportFile.absolutePath) // 完美带走全部信息

                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, tempExportFile.name)
                                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
                            }
                            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { u ->
                                context.contentResolver.openOutputStream(u)?.use { it.write(tempExportFile.readBytes()) }
                            }
                            tempExportFile.delete()
                            withContext(Dispatchers.Main) { Toast.makeText(context, "邮票已保存到相册", Toast.LENGTH_SHORT).show() }
                        }
                        stampForOptions = null
                    }
                )
                // --- 新增选项 2：保存原图 (正方形无边框) ---
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("保存原图 (无边框)") }, leadingContent = { Icon(Icons.Default.Image, null) },
                    modifier = Modifier.clickable {
                        val stamp = stampForOptions!!
                        scope.launch(Dispatchers.IO) {
                            val values = ContentValues().apply {
                                val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                put(MediaStore.Images.Media.DISPLAY_NAME, "STAMP_ORIGINAL_$timeStr.jpg")
                                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
                            }
                            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { u ->
                                // FileInputStream 直接倒流，连原本 Exif 都一起流进去
                                context.contentResolver.openOutputStream(u)?.use { outStream ->
                                    FileInputStream(stamp.file).use { inStream -> inStream.copyTo(outStream) }
                                }
                            }
                            withContext(Dispatchers.Main) { Toast.makeText(context, "无边框原图已保存到相册", Toast.LENGTH_SHORT).show() }
                        }
                        stampForOptions = null
                    }
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("添加 / 修改备注") }, leadingContent = { Icon(Icons.Default.Edit, null) },
                    modifier = Modifier.clickable {
                        remarkText = stampForOptions!!.remark
                        showRemarkDialog = stampForOptions
                        stampForOptions = null
                    }
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("删除", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        val stamp = stampForOptions!!
                        scope.launch(Dispatchers.IO) {
                            stamp.file.delete()
                            val pngName = stamp.fileName.replace(".jpg", ".png")
                            context.contentResolver.query(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                                "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(pngName), null
                            )?.use {
                                if (it.moveToFirst()) {
                                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                                    try { context.contentResolver.delete(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), null, null) }
                                    catch (e: Exception) { Log.e("Delete", "相册删除失败", e) }
                                }
                            }
                            load()
                        }
                        stampForOptions = null
                    }
                )
            }
        }
    }

    if (showRemarkDialog != null) {
        AlertDialog(
            onDismissRequest = { showRemarkDialog = null },
            title = { Text("邮票备注") },
            text = {
                OutlinedTextField(
                    value = remarkText, onValueChange = { if (it.length <= 50) remarkText = it },
                    label = { Text("最多50字") }, singleLine = false, maxLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val stamp = showRemarkDialog!!
                    scope.launch(Dispatchers.IO) {
                        val exif = ExifInterface(stamp.file.absolutePath)
                        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, encodeRemark(remarkText))
                        exif.saveAttributes()
                        load()
                    }
                    showRemarkDialog = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRemarkDialog = null }) { Text("取消") } }
        )
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
        color = Color.White, shadowElevation = 12.dp
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

                    if (stamp.remark.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("备注: ${stamp.remark}", fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    }

                    Spacer(Modifier.weight(1f))
                    ComposeCanvas(Modifier.size(60.dp)) { drawCircle(Color.LightGray, style = androidx.compose.ui.graphics.drawscope.Stroke(2f)) }
                    Text("*已支付邮资*", fontSize = 10.sp, color = Color.LightGray)
                }
            }
        }
    }
}