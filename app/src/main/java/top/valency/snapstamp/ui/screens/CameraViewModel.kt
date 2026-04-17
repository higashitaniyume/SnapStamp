package top.valency.snapstamp.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.valency.snapstamp.data.AppSettings
import top.valency.snapstamp.utils.processAndSaveStamp
import top.valency.snapstamp.utils.takePhotoWithPermission
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat

class CameraViewModel : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processedStampBitmap = MutableStateFlow<Bitmap?>(null)
    val processedStampBitmap: StateFlow<Bitmap?> = _processedStampBitmap.asStateFlow()

    fun takePhoto(
        context: Context,
        imageCapture: ImageCapture,
        fusedLocationClient: FusedLocationProviderClient,
        settings: AppSettings,
        viewWidth: Int,
        viewHeight: Int,
        screenRect: RectF,
        onSuccess: (Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (_isProcessing.value) return
        _isProcessing.value = true

        takePhotoWithPermission(context, fusedLocationClient, settings.writeLocationExif) { loc ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = image.toBitmap()
                        val rotation = image.imageInfo.rotationDegrees
                        image.close()

                        viewModelScope.launch(Dispatchers.Default) {
                            val finalBitmap = try {
                                processAndSaveStamp(
                                    bitmap, rotation, context,
                                    screenRect, viewWidth, viewHeight, loc,
                                    jpegQuality = settings.jpegQuality,
                                    saveInternalCopy = settings.keepInternalCopy && settings.allowBackup,
                                    autoSaveToAlbum = settings.autoSaveAfterShot && settings.saveToSystemAlbum,
                                    borderStrength = settings.borderThickness,
                                    borderClassicStyle = settings.borderClassicStyle,
                                    infoVisibleOverlay = settings.infoVisibleOverlay
                                )
                            } catch (e: Exception) {
                                null
                            }

                            withContext(Dispatchers.Main) {
                                _isProcessing.value = false
                                if (finalBitmap != null && !finalBitmap.isRecycled) {
                                    onSuccess(finalBitmap)
                                    if (settings.dropAnimation) {
                                        _processedStampBitmap.value = finalBitmap
                                    }
                                } else {
                                    onError(Exception("Failed to process image"))
                                }
                            }
                        }
                    }

                    override fun onError(exc: ImageCaptureException) {
                        _isProcessing.value = false
                        onError(exc)
                    }
                }
            )
        }
    }

    fun clearProcessedBitmap() {
        _processedStampBitmap.value = null
    }
}
