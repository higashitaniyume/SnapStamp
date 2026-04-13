package top.valency.snapstamp.ui.screens

import android.graphics.RectF
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.valency.snapstamp.utils.processAndSaveStamp
import top.valency.snapstamp.utils.takePhotoWithPermission

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