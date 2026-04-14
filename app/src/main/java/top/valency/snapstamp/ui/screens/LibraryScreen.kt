package top.valency.snapstamp.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.valency.snapstamp.model.StampModel
import top.valency.snapstamp.ui.components.FlipStampCard
import top.valency.snapstamp.ui.components.StampItem
import top.valency.snapstamp.utils.applyOilPaintingFilter
import top.valency.snapstamp.utils.createStampBitmap
import top.valency.snapstamp.utils.decodeRemark
import top.valency.snapstamp.utils.encodeRemark
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- 状态管理 ---
    var stampList by remember { mutableStateOf<List<StampModel>>(emptyList()) }
    var selectedStampForPreview by remember { mutableStateOf<StampModel?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<StampModel>() }

    // 当前大图预览的文件（原图或油画图）
    var currentDisplayFile by remember { mutableStateOf<File?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    // 对话框状态
    var showDeleteConfirm by remember { mutableStateOf<List<StampModel>?>(null) }
    var showRemarkDialog by remember { mutableStateOf<StampModel?>(null) }
    var remarkText by remember { mutableStateOf("") }

    // --- 加载数据 ---
    fun load() {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            val files = context.filesDir.listFiles { f ->
                f.name.startsWith("STAMP_") && f.name.endsWith(".jpg") && !f.name.contains("_OIL")
            }?.toList() ?: emptyList()

            val mapped = mutableListOf<StampModel>()
            for (f in files.sortedByDescending { it.lastModified() }) {
                kotlinx.coroutines.yield()
                val exif = ExifInterface(f.absolutePath)
                mapped.add(
                    StampModel(
                        fileName = f.name, file = f,
                        date = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "未知日期",
                        info = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "未知设备",
                        location = exif.latLong?.let { "${String.format("%.2f", it[0])}, ${String.format("%.2f", it[1])}" } ?: "无位置信息",
                        remark = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { decodeRemark(it) } ?: ""
                    )
                )
            }
            withContext(Dispatchers.Main) { stampList = mapped; isOperating = false }
        }
    }
    LaunchedEffect(Unit) { load() }

    // --- 核心操作逻辑 ---

    // 滤镜生成逻辑
    fun toggleOilFilter(stamp: StampModel, enable: Boolean) {
        val oilFile = File(stamp.file.absolutePath.replace(".jpg", "_OIL.jpg"))
        if (enable) {
            if (oilFile.exists()) {
                currentDisplayFile = oilFile
            } else {
                isOperating = true
                scope.launch(Dispatchers.IO) {
                    val original = BitmapFactory.decodeFile(stamp.file.absolutePath) ?: return@launch
                    val maxDim = 800f
                    val scale = minOf(1f, maxDim / maxOf(original.width, original.height))
                    val processBitmap = if (scale < 1f) {
                        val scaled = original.scale(
                            (original.width * scale).toInt(),
                            (original.height * scale).toInt()
                        )
                        original.recycle()
                        scaled
                    } else {
                        original
                    }
                    val filtered = applyOilPaintingFilter(processBitmap, radius = 7, levels = 20)
                    FileOutputStream(oilFile).use { out -> filtered.compress(Bitmap.CompressFormat.JPEG, 100, out) }
                    processBitmap.recycle()
                    filtered.recycle()
                    withContext(Dispatchers.Main) { currentDisplayFile = oilFile; isOperating = false }
                }
            }
        } else {
            currentDisplayFile = stamp.file
        }
    }

    // 保存到相册
    fun performSave(stamps: List<StampModel>, withBorder: Boolean) {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            stamps.forEach { stamp ->
                try {
                    val fileToProcess = if (stamps.size == 1 && selectedStampForPreview == stamp) currentDisplayFile ?: stamp.file else stamp.file
                    val isOil = fileToProcess.name.contains("_OIL")
                    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                    val displayName = if (withBorder) "STAMP_${if(isOil)"OIL_" else ""}$timeStr.png" else "PHOTO_${if(isOil)"OIL_" else ""}$timeStr.jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, if (withBorder) "image/png" else "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
                    }
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            if (withBorder) {
                                val bitmap = BitmapFactory.decodeFile(fileToProcess.absolutePath)
                                val stamped = createStampBitmap(bitmap)
                                stamped.compress(Bitmap.CompressFormat.PNG, 100, out)
                            } else {
                                FileInputStream(fileToProcess).use { it.copyTo(out) }
                            }
                        }
                    }
                } catch (e: Exception) { Log.e("Save", "Error", e) }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                isOperating = false
            }
        }
    }

    // 分享
    fun performShare(stamps: List<StampModel>, withBorder: Boolean) {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            try {
                val uris = ArrayList<Uri>()
                stamps.forEach { stamp ->
                    val fileToProcess = if (stamps.size == 1 && selectedStampForPreview == stamp) currentDisplayFile ?: stamp.file else stamp.file
                    val fileToShare = if (withBorder) {
                        val bitmap = BitmapFactory.decodeFile(fileToProcess.absolutePath)
                        val stamped = createStampBitmap(bitmap)
                        val temp = File(context.cacheDir, "SHARE_${System.currentTimeMillis()}.png")
                        FileOutputStream(temp).use { stamped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        temp
                    } else fileToProcess
                    uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileToShare))
                }
                val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                    type = "image/*"
                    if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    else putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享图片"))
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) { isOperating = false }
        }
    }

    // 删除
    fun performDelete(stamps: List<StampModel>) {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            stamps.forEach { stamp ->
                if (stamp.file.exists()) stamp.file.delete()
                val oilFile = File(stamp.file.absolutePath.replace(".jpg", "_OIL.jpg"))
                if (oilFile.exists()) oilFile.delete()
            }
            withContext(Dispatchers.Main) {
                selectedStampForPreview = null
                isSelectionMode = false
                selectedItems.clear()
                load()
            }
        }
    }

    BackHandler(isSelectionMode || selectedStampForPreview != null) {
        if (selectedStampForPreview != null) selectedStampForPreview = null
        else { isSelectionMode = false; selectedItems.clear() }
    }

    // --- UI 界面 ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) "已选择 ${selectedItems.size}" else "我的集邮册") },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { isSelectionMode = false; selectedItems.clear() }) { Icon(Icons.Default.Close, null) }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = isSelectionMode, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = { performShare(selectedItems.toList(), true) }) { Icon(Icons.Default.Share, null) }
                        IconButton(onClick = { performShare(selectedItems.toList(), false) }) { Icon(Icons.Default.IosShare, null) }
                        VerticalDivider(modifier = Modifier.padding(12.dp))
                        IconButton(onClick = { performSave(selectedItems.toList(), true) }) { Icon(Icons.Default.Save, null) }
                        IconButton(onClick = { performSave(selectedItems.toList(), false) }) { Icon(Icons.Default.Image, null) }
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showDeleteConfirm = selectedItems.toList() }, containerColor = MaterialTheme.colorScheme.errorContainer) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // --- 修复点：在这里对日期进行格式化处理 ---
            val groupedStamps = remember(stampList) {
                stampList.groupBy { stamp ->
                    val rawDate = stamp.date.take(10) // 原始格式 "2026:01:01"
                    try {
                        val inputFormat = SimpleDateFormat("yyyy:MM:dd", Locale.getDefault())
                        val outputFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
                        val dateObj = inputFormat.parse(rawDate)
                        if (dateObj != null) outputFormat.format(dateObj) else rawDate
                    } catch (e: Exception) {
                        rawDate
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedStamps.forEach { (date, stamps) ->
                    item(span = { GridItemSpan(maxLineSpan) }) { Text(date, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
                    items(stamps) { stamp ->
                        val isSelected = selectedItems.contains(stamp)
                        Box {
                            StampItem(
                                stamp = stamp,
                                onClick = {
                                    if (isSelectionMode) { if (isSelected) selectedItems.remove(stamp) else selectedItems.add(stamp) }
                                    else {
                                        selectedStampForPreview = stamp
                                        currentDisplayFile = stamp.file
                                    }
                                },
                                onLongClick = { if(!isSelectionMode) { isSelectionMode = true; selectedItems.add(stamp) } }
                            )
                            if (isSelectionMode && isSelected) {
                                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // --- 大图预览 OverLay ---
            AnimatedVisibility(selectedStampForPreview != null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { selectedStampForPreview = null }, Alignment.Center) {
                    selectedStampForPreview?.let { stamp ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = false) {}) {
                            FlipStampCard(stamp = stamp, displayFile = currentDisplayFile)
                            Spacer(Modifier.height(24.dp))
                            Row(Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape).padding(4.dp)) {
                                val isOil = currentDisplayFile?.name?.contains("_OIL") == true
                                FilterTab("原图", !isOil) { toggleOilFilter(stamp, false) }
                                FilterTab("油画", isOil) { toggleOilFilter(stamp, true) }
                            }
                            Spacer(Modifier.height(24.dp))
                            Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(32.dp)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PreviewActionBtn(Icons.Default.Edit, "备注") { remarkText = stamp.remark; showRemarkDialog = stamp }
                                    PreviewActionBtn(Icons.Default.Save, "邮票") { performSave(listOf(stamp), true) }
                                    PreviewActionBtn(Icons.Default.Image, "原图") { performSave(listOf(stamp), false) }
                                    PreviewActionBtn(Icons.Default.Share, "分享邮票") { performShare(listOf(stamp), true) }
                                    PreviewActionBtn(Icons.Default.IosShare, "分享原图") { performShare(listOf(stamp), false) }
                                    PreviewActionBtn(Icons.Default.Delete, "移除", Color.Red) { showDeleteConfirm = listOf(stamp) }
                                }
                            }
                        }
                    }
                }
            }

            if (isOperating) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("确认移除") },
            text = { Text("将彻底从集邮册中移除 ${showDeleteConfirm?.size} 张邮票及其滤镜副本。") },
            confirmButton = {
                Button(onClick = { performDelete(showDeleteConfirm!!); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认移除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }

    if (showRemarkDialog != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("编辑备注") },
            text = { OutlinedTextField(value = remarkText, onValueChange = { if (it.length <= 50) remarkText = it }) },
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
            }
        )
    }
}

@Composable
fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PreviewActionBtn(icon: ImageVector, label: String, color: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, color = color, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}