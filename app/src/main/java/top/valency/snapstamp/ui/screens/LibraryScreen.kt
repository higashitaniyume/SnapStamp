package top.valency.snapstamp.ui.screens

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import java.util.Date
import java.util.Locale

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