@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.aicamera.app.backend.camera

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.aicamera.app.backend.hdr.HdrService
import com.aicamera.app.backend.models.CameraParams
import com.aicamera.app.backend.models.FlashMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.aicamera.app.backend.diagnostics.PerformanceTracer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object CameraBackend {
    private const val TAG = "CameraBackend"
    private var hdrService: HdrService? = null

    /** 专用于 takePicture 回调的后台线程，避免主线程阻塞 */
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val settingsListeners = mutableListOf<() -> Unit>()
    
    fun addSettingsListener(listener: () -> Unit) {
        settingsListeners.add(listener)
    }
    
    fun removeSettingsListener(listener: () -> Unit) {
        settingsListeners.remove(listener)
    }
    
    private fun notifySettingsChanged() {
        settingsListeners.forEach { it.invoke() }
    }
    
    object ManualSettings {
        private val _aspectRatioFlow = MutableSharedFlow<Float>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val aspectRatioFlow: SharedFlow<Float> = _aspectRatioFlow.asSharedFlow()

        var iso: Int? = null
            set(value) {
                field = value
                incrementVersion()
                notifyChanged()
            }
        var exposureTimeNs: Long? = null
            set(value) {
                field = value
                incrementVersion()
                notifyChanged()
            }
        var evIndex: Int? = null
            set(value) {
                field = value
                incrementVersion()
                notifyChanged()
            }
        var hdrEnabled: Boolean = false
            set(value) {
                field = value
                incrementVersion()
                notifyChanged()
            }
        var previewAspectRatioPortrait: Float = 0.75f
            set(value) {
                field = value
                incrementVersion()
                notifyChanged()
                _aspectRatioFlow.tryEmit(value)
            }
        private var version: Int = 0

        fun getVersion(): Int = version

        private fun incrementVersion() {
            version++
        }
        
        private fun notifyChanged() {
            CameraBackend.notifySettingsChanged()
        }
        
        fun reset() {
            iso = null
            exposureTimeNs = null
            evIndex = null
            hdrEnabled = false
            previewAspectRatioPortrait = 0.75f
        }
    }

    fun capturePhoto(
        context: Context,
        imageCapture: ImageCapture?,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (imageCapture == null) {
            onError("相机未初始化")
            return
        }

        val e1 = PerformanceTracer.traceStart("takePicture", 0, "lens=$lensFacing")

        val timestamp = System.currentTimeMillis()
        val fileName = "IMG_${timestamp}.jpg"

        val parentDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
            ?: context.cacheDir
        val photoFile = File(parentDir, fileName)

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(photoFile)
            .build()

        // 参照 CameraXBasic 官方示例，使用后台线程 Executor 处理回调，
        // PhotonCamera 也是使用 HandlerThread("CameraBackground") 处理所有相机操作
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    PerformanceTracer.traceEnd(e1, "saved=${photoFile.name}")
                    Log.d(TAG, "capturePhoto ok: ${photoFile.absolutePath}")

                    val e2 = PerformanceTracer.traceStart("postProcess", 0)
                    val aspectRatio = ManualSettings.previewAspectRatioPortrait
                    // 合并 crop + mirror 为一次 decode → process → compress 流水线
                    val finalFile = processPhoto(photoFile, aspectRatio, lensFacing)

                    notifyMediaScanner(context, finalFile)
                    PerformanceTracer.traceEnd(e2, "path=${finalFile.name}")

                    // 切回主线程通知 UI
                    mainHandler.post {
                        onSuccess(finalFile.absolutePath)
                        PerformanceTracer.dump(0)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    PerformanceTracer.traceEnd(e1, "error=${exception.message}")
                    Log.e(TAG, "capturePhoto failed", exception)
                    mainHandler.post {
                        onError("拍照失败：${exception.message ?: "unknown"}")
                        PerformanceTracer.dump(0)
                    }
                }
            }
        )
    }
    
    fun captureHdrPhoto(
        context: Context,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "Starting HDR capture")

        if (hdrService == null) {
            hdrService = HdrService.getInstance(context)
        }

        val service = hdrService!!

        // HDR 后处理放在 IO 线程，不在主线程做 crop/mirror
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cameraId = if (lensFacing == CameraSelector.LENS_FACING_BACK) "0" else "1"

                val result = service.captureHdr(cameraId)

                Log.d(TAG, "HDR capture completed: ${result.filePath}, ${result.processingTimeMs}ms, ${result.frameCount} frames")

                val aspectRatio = ManualSettings.previewAspectRatioPortrait
                var finalFile = File(result.filePath)

                if (aspectRatio > 0f && aspectRatio != 0.75f) {
                    finalFile = cropImageToAspectRatio(finalFile, aspectRatio)
                }

                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    finalFile = mirrorImageHorizontally(finalFile)
                }

                notifyMediaScanner(context, finalFile)

                withContext(Dispatchers.Main) {
                    onSuccess(finalFile.absolutePath)
                }

            } catch (e: Exception) {
                Log.e(TAG, "HDR capture failed", e)
                withContext(Dispatchers.Main) {
                    onError("HDR 拍照失败：${e.message ?: "unknown"}")
                }
            }
        }
    }
    
    fun isHdrCapturing(): Boolean {
        return hdrService?.isCapturing() ?: false
    }
    
    fun getHdrProgress(): Pair<Int, Int> {
        return hdrService?.getProgress() ?: Pair(0, 0)
    }
    
    fun initHdrService(context: Context) {
        if (hdrService == null) {
            hdrService = HdrService.getInstance(context)
        }
    }
    
    fun releaseHdrService() {
        hdrService?.release()
        hdrService = null
    }

    /**
     * 统一的后处理流水线：一次 decode → crop(可选) → mirror(可选) → compress(95)
     * 消除了旧的两次 decodeFile + 两次 compress(100) 的串行瓶颈。
     * PhotonCamera 将 crop/mirror 放在 OpenGL GPU 端；本实现退而求其次在 CPU 端单次完成。
     */
    private fun processPhoto(photoFile: File, aspectRatio: Float, lensFacing: Int): File {
        try {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return photoFile
            val width = bitmap.width
            val height = bitmap.height

            // Step 1: Crop（仅非 4:3 比例时裁剪）
            var processed = if (aspectRatio > 0f && abs(aspectRatio - 0.75f) > 0.001f) {
                val targetRatio = aspectRatio
                val cameraRatio = 0.75f
                val targetWidth: Int
                val targetHeight: Int
                if (targetRatio > cameraRatio) {
                    val squareSize = min(width, height)
                    targetWidth = squareSize
                    targetHeight = squareSize
                } else {
                    val cropRatio = targetRatio / cameraRatio
                    targetWidth = (width * cropRatio).toInt()
                    targetHeight = height
                }
                val x = (width - targetWidth) / 2
                val y = (height - targetHeight) / 2
                Bitmap.createBitmap(bitmap, x, y, targetWidth, targetHeight)
            } else if (aspectRatio < 0f) {
                val targetRatio = 9f / 19.5f
                val cameraRatio = 0.75f
                val cropRatio = targetRatio / cameraRatio
                val targetWidth = (width * cropRatio).toInt()
                val targetHeight = height
                val x = (width - targetWidth) / 2
                val y = (height - targetHeight) / 2
                Bitmap.createBitmap(bitmap, x, y, targetWidth, targetHeight)
            } else {
                bitmap
            }

            // Step 2: Mirror（前置摄像头时水平翻转）
            val final = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                val m = Matrix().apply { preScale(-1f, 1f, processed.width / 2f, processed.height / 2f) }
                Bitmap.createBitmap(processed, 0, 0, processed.width, processed.height, m, true)
            } else {
                processed
            }

            // Step 3: Compress（JPEG 95 ≈ 近乎无损，比 100 快 30-50%）
            FileOutputStream(photoFile).use { out ->
                final.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // 逐层释放不再使用的 bitmap
            if (final !== processed && processed !== bitmap) processed.recycle()
            if (final !== bitmap) bitmap.recycle()
            final.recycle()

            Log.d(TAG, "processPhoto: crop=${aspectRatio != 0.75f}, mirror=${lensFacing == CameraSelector.LENS_FACING_FRONT}, file=${photoFile.name}")
            return photoFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process photo", e)
            return photoFile
        }
    }

    /** 保留用于 HDR 拍照路径的裁剪（HDR 使用不同比例逻辑） */
    private fun cropImageToAspectRatio(photoFile: File, aspectRatio: Float): File {
        try {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return photoFile
            val width = bitmap.width
            val height = bitmap.height

            val targetWidth: Int
            val targetHeight: Int

            val isPortrait = height > width

            if (isPortrait) {
                val currentRatio = width.toFloat() / height
                if (currentRatio > aspectRatio) {
                    targetHeight = height
                    targetWidth = (height * aspectRatio).toInt()
                } else {
                    targetWidth = width
                    targetHeight = (width / aspectRatio).toInt()
                }
            } else {
                val landscapeRatio = 1f / aspectRatio
                val currentRatio = height.toFloat() / width
                if (currentRatio > landscapeRatio) {
                    targetWidth = width
                    targetHeight = (width * landscapeRatio).toInt()
                } else {
                    targetHeight = height
                    targetWidth = (height / landscapeRatio).toInt()
                }
            }

            val x = (width - targetWidth) / 2
            val y = (height - targetHeight) / 2

            val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, targetWidth, targetHeight)

            FileOutputStream(photoFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            if (croppedBitmap != bitmap) {
                bitmap.recycle()
                croppedBitmap.recycle()
            } else {
                bitmap.recycle()
            }

            Log.d(TAG, "Image cropped to ${targetWidth}x${targetHeight}, ratio=${aspectRatio}")
            return photoFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop image", e)
            return photoFile
        }
    }

    /** 保留用于 HDR 拍照路径的水平镜像 */
    private fun mirrorImageHorizontally(photoFile: File): File {
        try {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return photoFile
            val width = bitmap.width
            val height = bitmap.height

            val matrix = Matrix().apply {
                preScale(-1f, 1f, width / 2f, height / 2f)
            }

            val mirroredBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)

            FileOutputStream(photoFile).use { out ->
                mirroredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            if (mirroredBitmap != bitmap) {
                bitmap.recycle()
                mirroredBitmap.recycle()
            } else {
                bitmap.recycle()
            }

            Log.d(TAG, "Image mirrored horizontally for front camera")
            return photoFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to mirror image", e)
            return photoFile
        }
    }
    
    private fun notifyMediaScanner(context: Context, photoFile: File) {
        try {
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val contentUri = Uri.fromFile(photoFile)
            mediaScanIntent.setData(contentUri)
            context.sendBroadcast(mediaScanIntent)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, photoFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                try {
                    context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                } catch (e: Exception) {
                }
            }
            
            Log.d(TAG, "Media scanner notified: ${photoFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify media scanner", e)
        }
    }

    fun setFlashMode(imageCapture: ImageCapture?, mode: FlashMode) {
        if (imageCapture == null) return
        imageCapture.flashMode = when (mode) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun switchCamera(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        preview: Preview,
        imageCapture: ImageCapture,
        currentFacing: Int
    ): Int {
        val newFacing = if (currentFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        val selector = CameraSelector.Builder()
            .requireLensFacing(newFacing)
            .build()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        return newFacing
    }

    fun getCameraParams(camera: Camera?): CameraParams {
        if (camera == null) return CameraParams("Auto", "Auto", "Auto")

        val info = camera.cameraInfo

        val isoStr = if (ManualSettings.iso != null) {
            ManualSettings.iso.toString()
        } else {
            "Auto"
        }

        val shutterStr = if (ManualSettings.exposureTimeNs != null) {
            val exposureNs = ManualSettings.exposureTimeNs!!
            if (exposureNs == 0L) {
                "Auto"
            } else if (exposureNs >= 1_000_000_000L) {
                val seconds = exposureNs.toDouble() / 1_000_000_000.0
                if (seconds >= 1.0) {
                    "${seconds.toInt()}s"
                } else {
                    val denom = (1.0 / seconds).toInt()
                    if (denom == 0) "Auto" else "1/${denom}s"
                }
            } else {
                val denom = 1_000_000_000L / exposureNs
                if (denom == 0L) "Auto" else "1/${denom}s"
            }
        } else {
            val exposureIndex = info.exposureState.exposureCompensationIndex
            exposureIndexToShutterSpeed(exposureIndex)
        }

        val apertureStr = if (ManualSettings.evIndex != null) {
            "EV ${ManualSettings.evIndex}"
        } else {
            "Auto"
        }

        return CameraParams(iso = isoStr, shutter = shutterStr, aperture = apertureStr)
    }

    private fun exposureIndexToShutterSpeed(exposureIndex: Int?): String {
        if (exposureIndex == null) return "Auto"
        val base = 1.0 / 125.0
        val factor = Math.pow(2.0, exposureIndex.toDouble())
        val seconds = base * factor
        val denom = max(1, (1.0 / seconds).toInt())
        return "1/${denom}s"
    }

    private fun trimOneDecimal(v: Float): String {
        val scaled = (v * 10f).toInt() / 10f
        return if (abs(scaled - scaled.toInt()) < 1e-6) scaled.toInt().toString() else scaled.toString()
    }
    
    fun getCurrentSettingsSnapshot(): SettingsSnapshot {
        return SettingsSnapshot(
            iso = ManualSettings.iso,
            exposureTimeNs = ManualSettings.exposureTimeNs,
            evIndex = ManualSettings.evIndex,
            hdrEnabled = ManualSettings.hdrEnabled,
            previewAspectRatioPortrait = ManualSettings.previewAspectRatioPortrait
        )
    }
    
    data class SettingsSnapshot(
        val iso: Int?,
        val exposureTimeNs: Long?,
        val evIndex: Int?,
        val hdrEnabled: Boolean,
        val previewAspectRatioPortrait: Float
    )
}
