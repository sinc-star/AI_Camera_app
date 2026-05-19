package com.aicamera.app.ui.screens

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.aicamera.app.backend.camera.CameraPreloadManager
import com.aicamera.app.backend.camera.CameraSession
import com.aicamera.app.backend.diagnostics.PerformanceTracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 相机初始化管理器 — 封装相机初始化、重绑定和切换逻辑
 *
 * 职责：
 * - 相机初次初始化（Preview + ImageCapture 构造、绑定生命周期）
 * - HDR/镜头切换时的相机重绑定
 * - ExtensionsManager 延迟初始化
 * - 翻转摄像头操作
 * - 相机参数优化配置
 */
@Composable
fun CameraInitManager(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    displayRotation: Int,
    cameraTargetAspectRatio: Int,
    lensFacingState: MutableState<Int>,
    hdrEnabled: Boolean,
    cameraProviderState: MutableState<ProcessCameraProvider?>,
    previewUseCaseState: MutableState<Preview?>,
    imageCaptureState: MutableState<ImageCapture?>,
    cameraState: MutableState<Camera?>,
    extensionsManagerState: MutableState<ExtensionsManager?>,
    isCameraSwitchingState: MutableState<Boolean>,
    hdrExtensionAvailable: MutableMap<Int, Boolean>
) {
    // 上次相机状态，用于判断是否需要重绑定
    var lastLensFacing by remember { mutableStateOf(lensFacingState.value) }
    var lastHdrEnabled by remember { mutableStateOf(hdrEnabled) }

    // 初始化相机
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val initTraceId = PerformanceTracer.traceClick("CameraInit", "lens=${lensFacingState.value}")
        Log.d("CameraInit", "开始初始化相机")

        // 优先使用预加载的相机提供者
        val e1 = PerformanceTracer.traceStart("GetCameraProvider", initTraceId)
        val provider = CameraPreloadManager.getPreloadedCameraProvider()
            ?: ProcessCameraProvider.getInstance(context).get()
        cameraProviderState.value = provider
        PerformanceTracer.traceEnd(e1)

        val providerTime = System.currentTimeMillis() - startTime
        Log.d("CameraInit", "相机提供者获取耗时: ${providerTime}ms")

        // 在后台线程构造 Preview/ImageCapture
        val e2 = PerformanceTracer.traceStart("BuildPreview", initTraceId)
        val preview = withContext(Dispatchers.Default) {
            Preview.Builder()
                .setTargetRotation(displayRotation)
                .setTargetAspectRatio(cameraTargetAspectRatio)
                .build()
        }
        preview.setSurfaceProvider(previewView.surfaceProvider)
        previewUseCaseState.value = preview
        PerformanceTracer.traceEnd(e2)

        val e3 = PerformanceTracer.traceStart("BuildImageCapture", initTraceId)
        val capture = withContext(Dispatchers.Default) {
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(100)
                .setTargetRotation(displayRotation)
                .build()
        }
        imageCaptureState.value = capture
        PerformanceTracer.traceEnd(e3)

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacingState.value)
            .build()

        try {
            val e4 = PerformanceTracer.traceStart("BindToLifecycle", initTraceId)
            provider.unbindAll()
            cameraState.value = provider.bindToLifecycle(
                lifecycleOwner, selector, preview, capture
            )
            CameraSession.set(cameraState.value, capture)
            PerformanceTracer.traceEnd(e4)

            configureCameraParams(cameraState.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val totalTime = System.currentTimeMillis() - startTime
        Log.d("CameraInit", "相机初始化完成，总耗时: ${totalTime}ms")
        PerformanceTracer.dump(initTraceId)

        // 延迟初始化extensionsManager
        lifecycleOwner.lifecycleScope.launch {
            extensionsManagerState.value = try {
                ExtensionsManager.getInstanceAsync(context, provider).get()
            } catch (_: Throwable) {
                null
            }
        }
    }

    // HDR / 翻转摄像头时重新绑定相机
    LaunchedEffect(hdrEnabled, lensFacingState.value, cameraProviderState.value,
        imageCaptureState.value, extensionsManagerState.value
    ) {
        val provider = cameraProviderState.value ?: return@LaunchedEffect
        val capture = imageCaptureState.value ?: return@LaunchedEffect

        val needRebind = lensFacingState.value != lastLensFacing || hdrEnabled != lastHdrEnabled
        if (!needRebind) return@LaunchedEffect

        val rebindTraceId = PerformanceTracer.traceClick(
            "CameraRebind", "lens=${lensFacingState.value},hdr=$hdrEnabled"
        )
        Log.d("CameraInit", "[相机重绑定] 开始: lensFacing=${lensFacingState.value}, hdrEnabled=$hdrEnabled")
        val rebindStart = System.currentTimeMillis()
        isCameraSwitchingState.value = true
        lastLensFacing = lensFacingState.value
        lastHdrEnabled = hdrEnabled

        val baseSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacingState.value)
            .build()
        val selector = if (hdrEnabled) {
            val mgr = extensionsManagerState.value
            if (mgr != null && hdrExtensionAvailable.getOrPut(lensFacingState.value) {
                    mgr.isExtensionAvailable(baseSelector, ExtensionMode.HDR)
                }) {
                mgr.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
            } else {
                baseSelector
            }
        } else {
            baseSelector
        }

        try {
            val e1 = PerformanceTracer.traceStart("unbindAll", rebindTraceId)
            provider.unbindAll()
            PerformanceTracer.traceEnd(e1)
            val e2 = PerformanceTracer.traceStart("bindToLifecycle", rebindTraceId)
            cameraState.value = provider.bindToLifecycle(
                lifecycleOwner, selector, previewUseCaseState.value!!, capture
            )
            CameraSession.set(cameraState.value, capture)
            PerformanceTracer.traceEnd(e2)

            isCameraSwitchingState.value = false
            cameraState.value?.cameraControl?.setZoomRatio(0.5f)

            configureCameraParams(cameraState.value, enableOis = true)

            val rebindElapsed = System.currentTimeMillis() - rebindStart
            Log.d("CameraInit", "[相机重绑定] 完成，耗时: ${rebindElapsed}ms")
            PerformanceTracer.dump(rebindTraceId)
        } catch (e: Throwable) {
            Log.e("CameraInit", "[相机重绑定] 失败", e)
        }
    }
}

private fun configureCameraParams(camera: Camera?, enableOis: Boolean = false) {
    try {
        val currentCamera = camera ?: throw IllegalStateException("相机未绑定")
        val camera2Control =
            androidx.camera.camera2.interop.Camera2CameraControl.from(currentCamera.cameraControl)
        val builder = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
            .setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
            )
            .setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        if (enableOis) {
            builder.setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
            )
        }
        camera2Control.setCaptureRequestOptions(builder.build())
        Log.d("CameraInit", "相机优化参数已配置")
    } catch (e: Throwable) {
        Log.e("CameraInit", "无法配置相机优化参数", e)
    }
}
