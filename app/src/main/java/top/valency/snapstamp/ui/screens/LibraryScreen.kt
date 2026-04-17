package top.valency.snapstamp.ui.screens

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import top.valency.snapstamp.data.AppSettings
import top.valency.snapstamp.data.SettingsStore
import top.valency.snapstamp.model.StampModel
import top.valency.snapstamp.R
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

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember(context) { SettingsStore(context) }
    val settings by settingsStore.settingsFlow.collectAsState(initial = AppSettings())
    val unknownDateText = stringResource(R.string.library_unknown_date)
    val unknownDeviceText = stringResource(R.string.library_unknown_device)
    val unknownLocationText = stringResource(R.string.library_unknown_location)
    val dateDisplayPattern = stringResource(R.string.library_date_display_format)

    // --- 状态管理 ---
    var stampList by remember { mutableStateOf<List<StampModel>>(emptyList()) }
    var selectedStampForPreview by remember { mutableStateOf<StampModel?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<StampModel>() }

    var currentDisplayFile by remember { mutableStateOf<File?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    var showDeleteConfirm by remember { mutableStateOf<List<StampModel>?>(null) }
    var showRemarkDialog by remember { mutableStateOf<StampModel?>(null) }
    var remarkText by remember { mutableStateOf("") }

    // --- 加载数据 ---
    fun load() {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            // 修复点：改用外部数据目录读取图片
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val baseDir = File(externalDir, "SnapStamp/Raw")
            
            val files = if (baseDir.exists()) {
                baseDir.walk()
                    .filter { f -> 
                        f.isFile && f.name.startsWith("STAMP_") && f.name.endsWith(".jpg") && !f.name.contains("_OIL") 
                    }
                    .toList()
            } else {
                emptyList()
            }

            val mapped = mutableListOf<StampModel>()
            for (f in files.sortedByDescending { it.lastModified() }) {
                yield()
                val exif = ExifInterface(f.absolutePath)
                mapped.add(
                    StampModel(
                        fileName = f.name, file = f,
                        date = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: unknownDateText,
                        info = exif.getAttribute(ExifInterface.TAG_MODEL) ?: unknownDeviceText,
                        location = exif.latLong?.let { "${String.format("%.2f", it[0])}, ${String.format("%.2f", it[1])}" } ?: unknownLocationText,
                        remark = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { decodeRemark(it) } ?: ""
                    )
                )
            }
            withContext(Dispatchers.Main) { stampList = mapped; isOperating = false }
        }
    }
    LaunchedEffect(Unit) { load() }

    // --- 核心操作逻辑 ---

    fun toggleOilFilter(stamp: StampModel, enable: Boolean) {
        val oilFile = File(stamp.file.absolutePath.replace(".jpg", "_OIL.jpg"))
        if (enable) {
            if (settings.filterCacheEnabled && oilFile.exists()) {
                currentDisplayFile = oilFile
            } else {
                isOperating = true
                scope.launch(Dispatchers.IO) {
                    val original = BitmapFactory.decodeFile(stamp.file.absolutePath) ?: return@launch
                    if (settings.largeImageWarn && maxOf(original.width, original.height) > 2500) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.library_large_image_warn), Toast.LENGTH_SHORT).show()
                        }
                    }
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
                    val radius = (4 + settings.oilFilterStrength * 6).toInt().coerceIn(4, 10)
                    val levels = (26 - settings.oilFilterStrength * 12).toInt().coerceIn(10, 26)
                    val filtered = applyOilPaintingFilter(processBitmap, radius = radius, levels = levels)
                    val targetFile = if (settings.filterCacheEnabled) {
                        oilFile
                    } else {
                        File(context.cacheDir, "TEMP_OIL_${System.currentTimeMillis()}.jpg")
                    }
                    FileOutputStream(targetFile).use { out -> filtered.compress(Bitmap.CompressFormat.JPEG, 100, out) }
                    processBitmap.recycle()
                    filtered.recycle()
                    withContext(Dispatchers.Main) { currentDisplayFile = targetFile; isOperating = false }
                }
            }
        } else {
            currentDisplayFile = stamp.file
        }
    }

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
                                val stamped = createStampBitmap(
                                    cropped = bitmap,
                                    borderStrength = settings.borderThickness,
                                    classicStyle = settings.borderClassicStyle,
                                    showInfoOverlay = settings.infoVisibleOverlay,
                                    date = stamp.date,
                                    deviceInfo = stamp.info,
                                    location = stamp.location
                                )
                                stamped.compress(Bitmap.CompressFormat.PNG, 100, out)
                            } else {
                                FileInputStream(fileToProcess).use { it.copyTo(out) }
                            }
                        }
                    }
                } catch (e: Exception) { Log.e("Save", "Error", e) }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.library_saved_to_album), Toast.LENGTH_SHORT).show()
                isOperating = false
            }
        }
    }

    fun performShare(stamps: List<StampModel>, withBorder: Boolean) {
        isOperating = true
        scope.launch(Dispatchers.IO) {
            try {
                val uris = ArrayList<Uri>()
                stamps.forEach { stamp ->
                    val fileToProcess = if (stamps.size == 1 && selectedStampForPreview == stamp) currentDisplayFile ?: stamp.file else stamp.file
                    val fileToShare = if (withBorder) {
                        val bitmap = BitmapFactory.decodeFile(fileToProcess.absolutePath)
                        val stamped = createStampBitmap(
                            cropped = bitmap,
                            borderStrength = settings.borderThickness,
                            classicStyle = settings.borderClassicStyle,
                            showInfoOverlay = settings.infoVisibleOverlay,
                            date = stamp.date,
                            deviceInfo = stamp.info,
                            location = stamp.location
                        )
                        val temp = File(context.cacheDir, "SHARE_${System.currentTimeMillis()}.png")
                        FileOutputStream(temp).use { stamped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        temp
                    } else {
                        if (!settings.sharePrivacyCheck) {
                            fileToProcess
                        } else {
                            val temp = File(context.cacheDir, "SHARE_PRIV_${System.currentTimeMillis()}.jpg")
                            FileInputStream(fileToProcess).use { input ->
                                FileOutputStream(temp).use { output -> input.copyTo(output) }
                            }
                            try {
                                val exif = ExifInterface(temp.absolutePath)
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, null)
                                exif.saveAttributes()
                            } catch (_: Exception) { }
                            temp
                        }
                    }
                    uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileToShare))
                }
                val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                    type = "image/*"
                    if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    else putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.library_share_image)))
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) { isOperating = false }
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) {
                            context.getString(R.string.library_selected_count, selectedItems.size)
                        } else {
                            stringResource(R.string.library_title)
                        }
                    )
                },
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
            val groupedStamps = remember(stampList) {
                stampList.groupBy { stamp ->
                    val rawDate = stamp.date.take(10)
                    try {
                        val inputFormat = SimpleDateFormat("yyyy:MM:dd", Locale.getDefault())
                        val outputFormat = SimpleDateFormat(dateDisplayPattern, Locale.getDefault())
                        val dateObj = inputFormat.parse(rawDate)
                        if (dateObj != null) outputFormat.format(dateObj) else rawDate
                    } catch (_: Exception) {
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
                                        if (settings.previewOilAsDefault) {
                                            toggleOilFilter(stamp, true)
                                        }
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

            AnimatedVisibility(selectedStampForPreview != null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { selectedStampForPreview = null }, Alignment.Center) {
                    selectedStampForPreview?.let { stamp ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = false) {}) {
                            FlipStampCard(stamp = stamp, displayFile = currentDisplayFile)
                            Spacer(Modifier.height(24.dp))
                            Row(Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape).padding(4.dp)) {
                                val isOil = currentDisplayFile?.name?.contains("_OIL") == true
                                FilterTab(stringResource(R.string.library_filter_original), !isOil) { toggleOilFilter(stamp, false) }
                                FilterTab(stringResource(R.string.library_filter_oil), isOil) { toggleOilFilter(stamp, true) }
                            }
                            Spacer(Modifier.height(24.dp))
                            Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(32.dp)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PreviewActionBtn(Icons.Default.Edit, stringResource(R.string.library_action_remark)) { remarkText = stamp.remark; showRemarkDialog = stamp }
                                    PreviewActionBtn(Icons.Default.Save, stringResource(R.string.library_action_stamp)) { performSave(listOf(stamp), settings.exportWithBorderDefault) }
                                    PreviewActionBtn(Icons.Default.Image, stringResource(R.string.library_action_original)) { performSave(listOf(stamp), !settings.exportWithBorderDefault) }
                                    PreviewActionBtn(Icons.Default.Share, stringResource(R.string.library_action_share_stamp)) { performShare(listOf(stamp), true) }
                                    PreviewActionBtn(Icons.Default.IosShare, stringResource(R.string.library_action_share_original)) { performShare(listOf(stamp), false) }
                                    PreviewActionBtn(Icons.Default.Delete, stringResource(R.string.library_action_remove), Color.Red) { showDeleteConfirm = listOf(stamp) }
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
            title = { Text(stringResource(R.string.library_delete_confirm_title)) },
            text = { Text(stringResource(R.string.library_delete_confirm_text, showDeleteConfirm?.size ?: 0)) },
            confirmButton = {
                Button(onClick = { performDelete(showDeleteConfirm!!); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.library_delete_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (showRemarkDialog != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.library_edit_remark_title)) },
            text = { OutlinedTextField(value = remarkText, onValueChange = { if (it.length <= 50) remarkText = it }) },
            confirmButton = {
                TextButton(onClick = {
                    val stamp = showRemarkDialog!!
                    scope.launch(Dispatchers.IO) {
                        if (!settings.writeRemarkExif) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.library_remark_exif_disabled), Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        val exif = ExifInterface(stamp.file.absolutePath)
                        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, encodeRemark(remarkText))
                        exif.saveAttributes()
                        load()
                    }
                    showRemarkDialog = null
                }) { Text(stringResource(R.string.common_save)) }
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
