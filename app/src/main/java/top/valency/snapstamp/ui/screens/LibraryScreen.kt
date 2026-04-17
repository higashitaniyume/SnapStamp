package top.valency.snapstamp.ui.screens

import android.annotation.SuppressLint
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import top.valency.snapstamp.R
import top.valency.snapstamp.data.AppSettings
import top.valency.snapstamp.data.SettingsStore
import top.valency.snapstamp.data.repository.StampRepository
import top.valency.snapstamp.model.StampModel
import top.valency.snapstamp.ui.components.FlipStampCard
import top.valency.snapstamp.ui.components.StampItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember(context) { SettingsStore(context) }
    val settings by settingsStore.settingsFlow.collectAsState(initial = AppSettings())
    
    // Simple ViewModel initialization (In a real app, use Hilt or a Factory)
    val repository = remember { StampRepository(context) }
    val viewModel: LibraryViewModel = viewModel { LibraryViewModel(repository, context) }

    val stampList by viewModel.stamps.collectAsState()
    val isOperating by viewModel.isOperating.collectAsState()

    val dateDisplayPattern = stringResource(R.string.library_date_display_format)

    // --- 状态管理 ---
    var selectedStampForPreview by remember { mutableStateOf<StampModel?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<StampModel>() }

    var currentDisplayFile by remember { mutableStateOf<File?>(null) }

    var showDeleteConfirm by remember { mutableStateOf<List<StampModel>?>(null) }
    var showRemarkDialog by remember { mutableStateOf<StampModel?>(null) }
    var remarkText by remember { mutableStateOf("") }

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
                        IconButton(onClick = { 
                            viewModel.shareImages(selectedItems.toList(), true, null, settings) { intent ->
                                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.library_share_image)))
                            }
                        }) { Icon(Icons.Default.Share, null) }
                        IconButton(onClick = { 
                            viewModel.shareImages(selectedItems.toList(), false, null, settings) { intent ->
                                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.library_share_image)))
                            }
                        }) { Icon(Icons.Default.IosShare, null) }
                        VerticalDivider(modifier = Modifier.padding(12.dp))
                        IconButton(onClick = { 
                            viewModel.saveToAlbum(selectedItems.toList(), true, null, settings) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Default.Save, null) }
                        IconButton(onClick = { 
                            viewModel.saveToAlbum(selectedItems.toList(), false, null, settings) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Default.Image, null) }
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
                                            scope.launch {
                                                currentDisplayFile = viewModel.generateOilFilterFile(stamp, settings)
                                            }
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
                                FilterTab(stringResource(R.string.library_filter_original), !isOil) { 
                                    currentDisplayFile = stamp.file 
                                }
                                FilterTab(stringResource(R.string.library_filter_oil), isOil) { 
                                    scope.launch {
                                        currentDisplayFile = viewModel.generateOilFilterFile(stamp, settings)
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(32.dp)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PreviewActionBtn(Icons.Default.Edit, stringResource(R.string.library_action_remark)) { remarkText = stamp.remark; showRemarkDialog = stamp }
                                    PreviewActionBtn(Icons.Default.Save, stringResource(R.string.library_action_stamp)) { 
                                        viewModel.saveToAlbum(listOf(stamp), settings.exportWithBorderDefault, currentDisplayFile, settings) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    PreviewActionBtn(Icons.Default.Image, stringResource(R.string.library_action_original)) { 
                                        viewModel.saveToAlbum(listOf(stamp), !settings.exportWithBorderDefault, currentDisplayFile, settings) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    PreviewActionBtn(Icons.Default.Share, stringResource(R.string.library_action_share_stamp)) { 
                                        viewModel.shareImages(listOf(stamp), true, currentDisplayFile, settings) { intent ->
                                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.library_share_image)))
                                        }
                                    }
                                    PreviewActionBtn(Icons.Default.IosShare, stringResource(R.string.library_action_share_original)) { 
                                        viewModel.shareImages(listOf(stamp), false, currentDisplayFile, settings) { intent ->
                                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.library_share_image)))
                                        }
                                    }
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
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.library_delete_confirm_title)) },
            text = { Text(stringResource(R.string.library_delete_confirm_text, showDeleteConfirm?.size ?: 0)) },
            confirmButton = {
                Button(onClick = { 
                    viewModel.deleteStamps(showDeleteConfirm!!) {
                        showDeleteConfirm = null
                        selectedStampForPreview = null
                        isSelectionMode = false
                        selectedItems.clear()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.library_delete_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (showRemarkDialog != null) {
        AlertDialog(
            onDismissRequest = { showRemarkDialog = null },
            title = { Text(stringResource(R.string.library_edit_remark_title)) },
            text = { OutlinedTextField(value = remarkText, onValueChange = { if (it.length <= 50) remarkText = it }) },
            confirmButton = {
                TextButton(onClick = {
                    val stamp = showRemarkDialog!!
                    if (!settings.writeRemarkExif) {
                        Toast.makeText(context, context.getString(R.string.library_remark_exif_disabled), Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateRemark(stamp, remarkText)
                    }
                    showRemarkDialog = null
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showRemarkDialog = null }) { Text(stringResource(R.string.common_cancel)) } }
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
