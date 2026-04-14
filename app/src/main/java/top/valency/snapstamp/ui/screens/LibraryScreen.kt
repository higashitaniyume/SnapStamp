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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import top.valency.snapstamp.utils.copyExif
import top.valency.snapstamp.utils.createStampBitmap
import top.valency.snapstamp.utils.decodeRemark
import top.valency.snapstamp.utils.encodeRemark
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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

    // 对话框状态
    var showDeleteConfirm by remember { mutableStateOf<List<StampModel>?>(null) }
    var showRemarkDialog by remember { mutableStateOf<StampModel?>(null) }
    var remarkText by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }

    // --- 核心逻辑 ---

    fun load() {
        scope.launch(Dispatchers.IO) {
            val files = context.filesDir.listFiles { f -> f.name.startsWith("STAMP_") && f.name.endsWith(".jpg") }?.toList() ?: emptyList()
            val mapped = files.sortedByDescending { it.lastModified() }.map { f ->
                val exif = ExifInterface(f.absolutePath)
                StampModel(
                    fileName = f.name, file = f,
                    date = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "未知日期",
                    info = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "未知设备",
                    location = exif.latLong?.let { "${String.format("%.2f", it[0])}, ${String.format("%.2f", it[1])}" } ?: "无位置信息",
                    remark = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { decodeRemark(it) } ?: ""
                )
            }
            withContext(Dispatchers.Main) {
                stampList = mapped
                isOperating = false
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    // 保存逻辑：利用 Scoped Storage 写入相册（Android 10+ 无需权限）
    fun performSave(stamps: List<StampModel>, withBorder: Boolean) {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            stamps.forEach { stamp ->
                try {
                    val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                    val displayName = if (withBorder) "STAMP_$timeStr.png" else "ORIGINAL_$timeStr.jpg"
                    val mimeType = if (withBorder) "image/png" else "image/jpeg"

                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapStamp")
                    }

                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let { targetUri ->
                        context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                            if (withBorder) {
                                val originalBitmap = BitmapFactory.decodeFile(stamp.file.absolutePath)
                                val stampBitmap = createStampBitmap(originalBitmap)
                                stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)

                                val tempFile = File(context.cacheDir, "temp_exif.png")
                                FileOutputStream(tempFile).use { stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                copyExif(stamp.file.absolutePath, tempFile.absolutePath)
                                tempFile.delete()
                            } else {
                                FileInputStream(stamp.file).use { it.copyTo(outStream) }
                            }
                        }
                    }
                } catch (e: Exception) { Log.e("Save", "Error", e) }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "已保存到相册 Pictures/SnapStamp", Toast.LENGTH_SHORT).show()
                isOperating = false
            }
        }
    }

    // 分享逻辑：修改为支持分享带边框的临时图或原始私有文件
    fun performShare(stamps: List<StampModel>, withBorder: Boolean) {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            try {
                val uris = ArrayList<Uri>()
                stamps.forEach { stamp ->
                    val fileToShare = if (withBorder) {
                        // 如果分享带边框的，先在缓存区生成一个临时 PNG
                        val originalBitmap = BitmapFactory.decodeFile(stamp.file.absolutePath)
                        val stampBitmap = createStampBitmap(originalBitmap)
                        val tempFile = File(context.cacheDir, "SHARE_${System.currentTimeMillis()}.png")
                        FileOutputStream(tempFile).use { out -> stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                        tempFile
                    } else {
                        // 否则直接分享私有目录里的原图 JPG
                        stamp.file
                    }

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        fileToShare
                    )
                    uris.add(uri)
                }

                if (uris.isNotEmpty()) {
                    val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                        type = if (withBorder) "image/png" else "image/jpeg"
                        if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        else putExtra(Intent.EXTRA_STREAM, uris[0])
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        context.startActivity(Intent.createChooser(intent, if (withBorder) "分享邮票" else "分享原图"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) { isOperating = false }
            }
        }
    }

    // 删除逻辑保持不变
    fun performDelete(stamps: List<StampModel>) {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            stamps.forEach { stamp ->
                if (stamp.file.exists()) {
                    stamp.file.delete()
                }
            }
            withContext(Dispatchers.Main) {
                selectedStampForPreview = null
                isSelectionMode = false
                selectedItems.clear()
                load()
                Toast.makeText(context, "已从集邮册中移除", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(isSelectionMode || selectedStampForPreview != null) {
        if (selectedStampForPreview != null) selectedStampForPreview = null
        else { isSelectionMode = false; selectedItems.clear() }
    }

    // --- UI 布局 ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) "已选择 ${selectedItems.size} 项" else "我的集邮册") },
                actions = {
                    if (isSelectionMode) {
                        TextButton(onClick = {
                            if (selectedItems.size == stampList.size) selectedItems.clear()
                            else { selectedItems.clear(); selectedItems.addAll(stampList) }
                        }) { Text(if (selectedItems.size == stampList.size) "取消全选" else "全选") }
                        IconButton(onClick = { isSelectionMode = false; selectedItems.clear() }) { Icon(Icons.Default.Close, null) }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = isSelectionMode, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                BottomAppBar(
                    actions = {
                        // 修改：增加分享原图/邮票的选项
                        IconButton(onClick = { performShare(selectedItems.toList(), true) }) { Icon(Icons.Default.Share, "分享邮票") }
                        IconButton(onClick = { performShare(selectedItems.toList(), false) }) { Icon(Icons.Default.IosShare, "分享原图") }
                        VerticalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                        IconButton(onClick = { performSave(selectedItems.toList(), true) }) { Icon(Icons.Default.Save, "保存邮票") }
                        IconButton(onClick = { performSave(selectedItems.toList(), false) }) { Icon(Icons.Default.Image, "保存原图") }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showDeleteConfirm = selectedItems.toList() },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        ) { Icon(Icons.Default.Delete, "移除") }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            val groupedStamps = stampList.groupBy {
                val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                val formatter = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                try { parser.parse(it.date)?.let { d -> formatter.format(d) } ?: "未知日期" } catch (e: Exception) { "未知日期" }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedStamps.forEach { (date, stamps) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(stamps) { stamp ->
                        val isSelected = selectedItems.contains(stamp)
                        Box {
                            StampItem(
                                stamp = stamp,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedItems.remove(stamp) else selectedItems.add(stamp)
                                    } else { selectedStampForPreview = stamp }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) { isSelectionMode = true; selectedItems.add(stamp) }
                                }
                            )
                            if (isSelectionMode) {
                                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f), CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                    if (isSelected) Icon(Icons.Default.Check, null, Modifier.size(16.dp), Color.White)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(selectedStampForPreview != null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable { selectedStampForPreview = null }, Alignment.Center) {
                    selectedStampForPreview?.let { stamp ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = false) {}) {
                            FlipStampCard(stamp)
                            Spacer(Modifier.height(32.dp))
                            Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(24.dp)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PreviewActionBtn(Icons.Default.Edit, "备注") { remarkText = stamp.remark; showRemarkDialog = stamp }
                                    PreviewActionBtn(Icons.Default.Save, "邮票") { performSave(listOf(stamp), true) }
                                    PreviewActionBtn(Icons.Default.Image, "原图") { performSave(listOf(stamp), false) }
                                    // 修改：增加分享邮票/分享原图
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
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认移除") },
            text = { Text("将从集邮册中移除这 ${showDeleteConfirm?.size} 张邮票。如果之前已保存到相册，相册中的照片不会被删除。") },
            confirmButton = {
                Button(onClick = { performDelete(showDeleteConfirm!!); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("确认移除")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }

    if (showRemarkDialog != null) {
        AlertDialog(
            onDismissRequest = { showRemarkDialog = null },
            title = { Text("编辑备注") },
            text = { OutlinedTextField(value = remarkText, onValueChange = { if (it.length <= 50) remarkText = it }, placeholder = { Text("记录此刻的心情...") }) },
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
fun PreviewActionBtn(icon: ImageVector, label: String, color: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}