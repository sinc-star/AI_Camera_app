package com.aicamera.app.ui.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aicamera.app.backend.ai.CloudAiService
import com.aicamera.app.backend.camera.CameraBackend
import com.aicamera.app.backend.diagnostics.DiagnosticsBackend
import com.aicamera.app.backend.diagnostics.PerformanceTracer
import com.aicamera.app.backend.gallery.GalleryBackend
import com.aicamera.app.backend.models.FlashMode
import com.aicamera.app.ui.components.*
import com.aicamera.app.ui.panels.ParamSettingsPanel
import com.aicamera.app.ui.theme.*
import com.aicamera.app.ui.theme.getColorScheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.util.concurrent.TimeUnit

/**
 * ============================================
 * 主相机界面（Camera Screen）
 * ============================================
 *
 * 功能说明：
 * - 相机预览
 * - 场景识别显示
 * - 构图辅助线 + AI 构图提示气泡
 * - 拍照功能
 * - 相机参数显示
 */

@Composable
fun CameraScreen(
    themeType: ThemeType,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // 权限管理
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // 场景识别结果
    LaunchedEffect(Unit) {
        cameraPermissionState.launchMultiplePermissionRequest()//请求权限
    }
    // 拍照后跳转到编辑界面,检查权限状态
    if (cameraPermissionState.isGranted) {
    CameraScreenContent(
        themeType = themeType,
        onNavigateToEdit = onNavigateToEdit,
        onNavigateToSettings = onNavigateToSettings
    )
    } else {
    PermissionRequestScreen(
        themeType = themeType,
        onRequestPermission = {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
    )
    }

}


@Composable
private fun CameraScreenContent(
    themeType: ThemeType,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val colorScheme = getColorScheme(themeType)

    // 设置沉浸式全屏，隐藏状态栏和导航栏
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isSystemInDarkTheme()
    LaunchedEffect(systemUiController, useDarkIcons) {
        systemUiController.isStatusBarVisible = false  // 完全隐藏状态栏
        systemUiController.isNavigationBarVisible = false  // 完全隐藏导航栏
        systemUiController.setStatusBarColor(Color.Transparent, darkIcons = useDarkIcons)
        systemUiController.setNavigationBarColor(Color.Transparent, darkIcons = useDarkIcons)
    }

    // 获取显示旋转方向（用于相机targetRotation）
    val displayRotation = remember {
        val display = context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay
        display?.rotation ?: android.view.Surface.ROTATION_0
    }

    // 相机状态
    val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }
    var imageCapture by imageCaptureState
    val previewView = remember { PreviewView(context) }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraProvider by cameraProviderState
    val previewUseCaseState = remember { mutableStateOf<Preview?>(null) }
    var previewUseCase by previewUseCaseState
    val cameraState = remember { mutableStateOf<Camera?>(null) }
    var camera by cameraState
    val lensFacingState = remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var lensFacing by lensFacingState

    // UI 状态
    val sceneTypeState = remember { mutableStateOf("通用拍摄") }
    var sceneType by sceneTypeState
    var showGuides by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var hdrEnabled by remember { mutableStateOf(false) }
    var zoomLinear by remember { mutableStateOf(0f) } // 线性变焦 0-1
    var zoomRatio by remember { mutableStateOf(1f) } // 实际变焦倍数（例如 1.0x, 2.0x）
    var zoomRatioRange by remember { mutableStateOf(1f..10f) } // 相机支持的变焦范围
    var showArcZoom by remember { mutableStateOf(false) } // 是否显示扇形变焦控件
    val zoomPresets = listOf(0.5f, 1f, 2f, 3f, 5f, 10f) // 预设变焦值
    val filteredPresets = zoomPresets.filter { it in zoomRatioRange } // 在相机支持范围内的预设值
    var timerSeconds by remember { mutableStateOf(0) }
    // 倒计时状态
    var countdownRemaining by remember { mutableStateOf(0) }
    var isCountingDown by remember { mutableStateOf(false) }
    val extensionsManagerState = remember { mutableStateOf<ExtensionsManager?>(null) }
    var extensionsManager: ExtensionsManager? by extensionsManagerState
    val hdrExtensionAvailable = remember { mutableMapOf<Int, Boolean>() }
    // 新增：独立面板显示状态
    var showParamSettingsPanel by remember { mutableStateOf(false) }

    // 相机参数（模拟数据，后端需要从相机获取真实数据）
    var iso by remember { mutableStateOf("Auto") }
    var shutter by remember { mutableStateOf("Auto") }
    var aperture by remember { mutableStateOf("Auto") }

    // AI 建议统一显示（云端优先，本地兜底）
    val currentTipState = remember { mutableStateOf("") }
    var currentTip by currentTipState
    val showTipState = remember { mutableStateOf(false) }
    var showTip by showTipState
    val currentTipSourceState = remember { mutableStateOf(TipSource.NONE) }
    var currentTipSource by currentTipSourceState

    // 云端AI建议
    val cloudAiTipState = remember { mutableStateOf("") }
    var cloudAiTip by cloudAiTipState
    val cloudAiTipPendingState = remember { mutableStateOf(false) }
    var cloudAiTipPending by cloudAiTipPendingState
    val detectedObjectsState = remember { mutableStateOf<List<String>>(emptyList()) }
    var detectedObjects by detectedObjectsState
    var cloudAiEnabled by remember { mutableStateOf(CloudAiService.hasApiKey(context)) }

    // 本地构图建议
    val compositionTipState = remember { mutableStateOf("") }
    var compositionTip by compositionTipState

    // 保存最新拍摄的照片 URI
    var lastCapturedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // 相册中最后一张照片的 URI（自动刷新）
    var lastGalleryPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // 定期刷新相册最新照片（每30秒检查一次，确保相册有更新时缩略图也能更新）
    LaunchedEffect(Unit) {
        while (isActive) {
            lastGalleryPhotoUri = GalleryBackend.getLastPhotoUri(context)
            kotlinx.coroutines.delay(30000) // 30秒刷新一次
        }
    }

    // 当 lastCapturedUri 变化时，立即重新获取相册最新照片（拍照后立即更新）
    LaunchedEffect(lastCapturedUri) {
        lastGalleryPhotoUri = GalleryBackend.getLastPhotoUri(context)
    }

    // 用于显示缩略图的最新照片 URI（优先显示刚拍的照片，否则显示相册最新照片）
    val latestPhotoUri = remember(lastCapturedUri, lastGalleryPhotoUri) {
        lastCapturedUri ?: lastGalleryPhotoUri
    }
    
    val density = LocalDensity.current
    val previewOffsetYPxDefault = with(density) { 0.dp.toPx() }  // 默认不偏移
    var previewOffsetYPx by remember { mutableStateOf(previewOffsetYPxDefault) }

    // 跟踪实际预览尺寸和显示区域
    var viewfinderBounds by remember { mutableStateOf(ViewfinderBounds.ZERO) }
    // 固定4:3的取景框位置，用于变焦表盘定位
    var viewfinderBounds43 by remember { mutableStateOf(ViewfinderBounds.ZERO) }
    // 画幅比例（从设置页同步）
    var previewAspectRatio by remember { mutableStateOf(CameraBackend.ManualSettings.previewAspectRatioPortrait) }
    // 相机输出比例（RATIO_4_3 或 RATIO_16_9），变化时触发相机重新绑定
    var cameraTargetAspectRatio by remember {
        mutableStateOf(toCameraAspectRatio(CameraBackend.ManualSettings.previewAspectRatioPortrait))
    }
    // 存储最新布局信息，供比例变化时重算 bounds
    var lastScreenInfo by remember { mutableStateOf<ScreenInfo?>(null) }
    // 相机切换动画状态
    val isCameraSwitchingState = remember { mutableStateOf(false) }
    var isCameraSwitching by isCameraSwitchingState
    
    // 动画状态管理 - 使用 Animatable 替代手动 while+delay(16) 循环，避免阻塞协程
    var transitionState by remember { mutableStateOf(TransitionState.IDLE) }
    val blurAnimatable = remember { Animatable(0f) }
    val boundsAnimatable = remember { Animatable(0f) }
    var startBounds by remember { mutableStateOf(ViewfinderBounds.ZERO) }
    var targetBounds by remember { mutableStateOf(ViewfinderBounds.ZERO) }
    var startRatio by remember { mutableStateOf(0.75f) }
    var targetRatio by remember { mutableStateOf(0.75f) }
    // 用于焦点环反馈的位置
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusRingAnimatable = remember { Animatable(0f) }

    // 事件驱动比例切换 — SharedFlow 立即响应，消除 200ms 轮询延迟
    // 采用PhotonCamera策略：比例切换只调整UI遮罩，不重新绑定相机
    // 使用 Animatable API 替代手动动画循环，由 Compose 帧调度器驱动动画
    LaunchedEffect(Unit) {
        CameraBackend.ManualSettings.aspectRatioFlow.collect { newRatio ->
            if (newRatio != previewAspectRatio) {
                val startTime = System.currentTimeMillis()
                Log.d("CameraScreen", "[比例切换] 开始: $previewAspectRatio -> $newRatio (仅UI调整)")

                // 记录初始状态
                startRatio = previewAspectRatio
                targetRatio = newRatio
                startBounds = viewfinderBounds

                // 计算目标边界
                val eResInfo = PerformanceTracer.traceStart("resolutionInfo", 0, "step=targetBounds")
                val cwTarget = previewUseCase?.resolutionInfo?.resolution?.width ?: 0
                val chTarget = previewUseCase?.resolutionInfo?.resolution?.height ?: 0
                PerformanceTracer.traceEnd(eResInfo, "w=$cwTarget,h=$chTarget")

                val e1 = PerformanceTracer.traceStart("computeTargetBounds", 0, "ratio=$newRatio")
                lastScreenInfo?.let { info ->
                    targetBounds = computeViewfinderBounds(
                        info.left, info.top, info.width, info.height,
                        newRatio, 0f, cwTarget, chTarget, displayRotation
                    )
                }
                PerformanceTracer.traceEnd(e1, "bounds=${targetBounds.width.toInt()}x${targetBounds.height.toInt()}")

                // 触发动画 — 使用 Animatable，Compose 帧调度器驱动，不阻塞协程线程
                transitionState = TransitionState.BLURRING
                blurAnimatable.snapTo(0f)
                boundsAnimatable.snapTo(0f)

                // 阶段1: 快速模糊（50ms）
                val eBlur = PerformanceTracer.traceStart("animBlur", 0)
                blurAnimatable.animateTo(1f, tween(50))
                PerformanceTracer.traceEnd(eBlur)

                // 阶段2: 清晰 + 边界过渡（150ms），两个动画并行
                transitionState = TransitionState.CLEARING
                val eClear = PerformanceTracer.traceStart("animClear", 0)
                coroutineScope {
                    launch { blurAnimatable.animateTo(0f, tween(150)) }
                    launch { boundsAnimatable.animateTo(1f, tween(150)) }
                }
                PerformanceTracer.traceEnd(eClear)

                // 完成动画
                transitionState = TransitionState.IDLE

                // 更新比例和UI
                val eState = PerformanceTracer.traceStart("updateState", 0)
                previewAspectRatio = newRatio
                PerformanceTracer.traceEnd(eState)

                // 同步 PreviewView scaleType
                val newScaleType = toPreviewScaleType(newRatio)
                val eScaleType = PerformanceTracer.traceStart("setScaleType", 0, "ratio=$newRatio,type=$newScaleType")
                previewView.scaleType = newScaleType
                PerformanceTracer.traceEnd(eScaleType)
                previewOffsetYPx = 0f  // 所有模式居中

                lastScreenInfo?.let { info ->
                    val eResInfo2 = PerformanceTracer.traceStart("resolutionInfo", 0, "step=finalBounds")
                    val cw = previewUseCase?.resolutionInfo?.resolution?.width ?: 0
                    val ch = previewUseCase?.resolutionInfo?.resolution?.height ?: 0
                    PerformanceTracer.traceEnd(eResInfo2)

                    val e2 = PerformanceTracer.traceStart("computeFinalBounds", 0)
                    viewfinderBounds = computeViewfinderBounds(
                        info.left, info.top, info.width, info.height,
                        newRatio, previewOffsetYPx, cw, ch, displayRotation
                    )
                    PerformanceTracer.traceEnd(e2, "bounds=${viewfinderBounds.width.toInt()}x${viewfinderBounds.height.toInt()}")

                    // viewfinderBounds43 仅由 onLayoutChangeListener 计算，此处不重算
                    val b = viewfinderBounds
                    val dm = context.resources.displayMetrics
                    // 诊断上报移到后台线程，避免阻塞主线程
                    val e5 = PerformanceTracer.traceStart("DiagnosticsReport", 0, "trigger=ratio_change")
                    withContext(Dispatchers.Default) {
                        DiagnosticsBackend.report(context, DiagnosticsBackend.Snapshot(
                            trigger = "ratio_change",
                            selectedRatioLabel = DiagnosticsBackend.getRatioLabel(newRatio),
                            selectedRatioValue = newRatio,
                            previewViewWidthPx = info.width,
                            previewViewHeightPx = info.height,
                            boundsLeft = b.left,
                            boundsTop = b.top,
                            boundsWidth = b.width,
                            boundsHeight = b.height,
                            offsetYPx = previewOffsetYPx,
                            densityDpi = dm.densityDpi,
                            screenWidthPx = dm.widthPixels,
                            screenHeightPx = dm.heightPixels,
                            cameraOutputWidth = cw,
                            cameraOutputHeight = ch
                        ))
                    }
                    PerformanceTracer.traceEnd(e5)
                }

                val elapsed = System.currentTimeMillis() - startTime
                Log.d("CameraScreen", "[比例切换] 完成，耗时: ${elapsed}ms")
                PerformanceTracer.dump(0)
            }
        }
    }

    // AI 分析协调器 — 管理bitmap生产者及三个AI消费者
    AiAnalysisCoordinator(
        showGuides = showGuides,
        cloudAiEnabled = cloudAiEnabled,
        previewView = previewView,
        context = context,
        iso = iso,
        shutter = shutter,
        sceneTypeState = sceneTypeState,
        detectedObjectsState = detectedObjectsState,
        currentTipState = currentTipState,
        showTipState = showTipState,
        currentTipSourceState = currentTipSourceState,
        cloudAiTipState = cloudAiTipState,
        cloudAiTipPendingState = cloudAiTipPendingState,
        compositionTipState = compositionTipState
    )

    // 相机初始化与重绑定管理器
    CameraInitManager(
        context = context,
        lifecycleOwner = lifecycleOwner,
        previewView = previewView,
        displayRotation = displayRotation,
        cameraTargetAspectRatio = cameraTargetAspectRatio,
        lensFacingState = lensFacingState,
        hdrEnabled = hdrEnabled,
        cameraProviderState = cameraProviderState,
        previewUseCaseState = previewUseCaseState,
        imageCaptureState = imageCaptureState,
        cameraState = cameraState,
        extensionsManagerState = extensionsManagerState,
        isCameraSwitchingState = isCameraSwitchingState,
        hdrExtensionAvailable = hdrExtensionAvailable
    )

    // 同步HDR设置
    LaunchedEffect(Unit) {
        while (isActive) {
            if (CameraBackend.ManualSettings.hdrEnabled != hdrEnabled) {
                hdrEnabled = CameraBackend.ManualSettings.hdrEnabled
            }
            kotlinx.coroutines.delay(500)
        }
    }

    // 闪光灯设置
    LaunchedEffect(flashEnabled) {
        CameraBackend.setFlashMode(imageCapture, if (flashEnabled) FlashMode.ON else FlashMode.OFF)
    }

    // 相机参数刷新
    LaunchedEffect(camera) {
        val c = camera ?: return@LaunchedEffect
        while (isActive) {
            val params = CameraBackend.getCameraParams(c)
            iso = params.iso
            shutter = params.shutter
            aperture = params.aperture
            c.cameraInfo.zoomState.value?.let { zoomState ->
                zoomLinear = zoomState.linearZoom
                zoomRatio = zoomState.zoomRatio
                zoomRatioRange = zoomState.minZoomRatio..zoomState.maxZoomRatio
            }
            kotlinx.coroutines.delay(500)
        }
    }

    // 翻转摄像头 — 仅改变状态，重绑定由CameraInitManager处理
    fun onFlipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        previewView.scaleX = if (lensFacing == CameraSelector.LENS_FACING_FRONT) -1f else 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(camera, previewView, context, latestPhotoUri) {
                // 双击切换摄像头
                detectTapGestures(
                    onDoubleTap = {
                        val tid = PerformanceTracer.traceClick("FlipCamera_DoubleTap")
                        onFlipCamera()
                        PerformanceTracer.dump(tid)
                    },
                    onTap = { tap ->
                        val tid = PerformanceTracer.traceClick("TapToFocus", "x=${tap.x.toInt()},y=${tap.y.toInt()}")
                        // 显示对焦环反馈动画
                        focusPoint = Offset(tap.x, tap.y)
                        scope.launch {
                            focusRingAnimatable.snapTo(1f)
                            focusRingAnimatable.animateTo(0f, tween(300))
                            focusPoint = null
                        }

                        val c = camera ?: return@detectTapGestures
                        try {
                            val factory = previewView.meteringPointFactory
                            val point: MeteringPoint = factory.createPoint(tap.x, tap.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            c.cameraControl.startFocusAndMetering(action)
                        } catch (_: Throwable) {
                        }
                        PerformanceTracer.dump(tid)
                    }
                )
            }
    ) {
        // 相机预览 - 根据比例使用 FIT_CENTER 或 FILL_CENTER
        AndroidView(
            factory = {
                previewView.apply {
                    scaleType = toPreviewScaleType(CameraBackend.ManualSettings.previewAspectRatioPortrait)
                    // 前置摄像头时禁用镜像（让预览显示非镜像图像）
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        previewView.scaleX = -1f
                    } else {
                        previewView.scaleX = 1f
                    }
                    // 监听布局变化，计算实际取景框区域
                    addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        // 仅在实际布局变化时重算（跳过动画导致的假触发）
                        if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) return@addOnLayoutChangeListener
                        val sw = v.width.toFloat()
                        val sh = v.height.toFloat()
                        val ratio = CameraBackend.ManualSettings.previewAspectRatioPortrait
                        lastScreenInfo = ScreenInfo(left.toFloat(), top.toFloat(), sw, sh)
                        val cameraOutputWidth = previewUseCase?.resolutionInfo?.resolution?.width ?: 0
                        val cameraOutputHeight = previewUseCase?.resolutionInfo?.resolution?.height ?: 0
                        viewfinderBounds = computeViewfinderBounds(
                            left.toFloat(), top.toFloat(), sw, sh, ratio, previewOffsetYPx,
                            cameraOutputWidth, cameraOutputHeight,
                            displayRotation
                        )
                        // 固定4:3取景框位置，用于表盘定位
                        viewfinderBounds43 = computeViewfinderBounds(
                            left.toFloat(), top.toFloat(), sw, sh, 0.75f, previewOffsetYPx,
                            cameraOutputWidth, cameraOutputHeight,
                            displayRotation
                        )
                        val b = viewfinderBounds
                        val dm = context.resources.displayMetrics
                        // 诊断 I/O 移到后台线程，避免主线程阻塞
                        scope.launch(Dispatchers.Default) {
                            DiagnosticsBackend.report(context, DiagnosticsBackend.Snapshot(
                                trigger = "layout_change",
                                selectedRatioLabel = DiagnosticsBackend.getRatioLabel(ratio),
                                selectedRatioValue = ratio,
                                previewViewWidthPx = sw,
                                previewViewHeightPx = sh,
                                boundsLeft = b.left,
                                boundsTop = b.top,
                                boundsWidth = b.width,
                                boundsHeight = b.height,
                                offsetYPx = previewOffsetYPx,
                                densityDpi = dm.densityDpi,
                                screenWidthPx = dm.widthPixels,
                                screenHeightPx = dm.heightPixels,
                                cameraOutputWidth = cameraOutputWidth,
                                cameraOutputHeight = cameraOutputHeight
                            ))
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoomRatioRange) {
                    var currentZoom = zoomRatio
                    detectTransformGestures { _, _, zoomDelta, _ ->
                        currentZoom = (currentZoom * zoomDelta).coerceIn(
                            zoomRatioRange.start,
                            zoomRatioRange.endInclusive
                        )
                        camera?.cameraControl?.setZoomRatio(currentZoom)
                    }
                }
        )

        // 取景框边框 - 仅显示拍摄范围边框，预览全屏显示（苹果相机风格）
        if (viewfinderBounds.width > 0) {
            val currentBounds = if (boundsAnimatable.value > 0f && startBounds != ViewfinderBounds.ZERO && targetBounds != ViewfinderBounds.ZERO) {
                ViewfinderBounds.lerp(startBounds, targetBounds, boundsAnimatable.value)
            } else {
                viewfinderBounds
            }
            ViewfinderMask(currentBounds, themeType)
        }

        // 构图辅助线 - 基于实际预览区域绘制，与取景框同步偏移
        if (showGuides && viewfinderBounds.width > 0) {
            val currentBounds = if (boundsAnimatable.value > 0f && startBounds != ViewfinderBounds.ZERO && targetBounds != ViewfinderBounds.ZERO) {
                ViewfinderBounds.lerp(startBounds, targetBounds, boundsAnimatable.value)
            } else {
                viewfinderBounds
            }
            CompositionGuides(currentBounds, themeType)

        }

        // 顶部栏 - 相机信息和设置按钮同行
        TopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            sceneType = sceneType,
            iso = iso,
            shutter = shutter,
            previewAspectRatio = previewAspectRatio,
            onAspectRatioChanged = { newRatio ->
                val tid = PerformanceTracer.traceClick("AspectRatioChange", "ratio=$newRatio")
                Log.d("CameraScreen", "画幅比例选择: $newRatio")
                previewAspectRatio = newRatio
                CameraBackend.ManualSettings.previewAspectRatioPortrait = newRatio
                PerformanceTracer.dump(tid, minDurationMs = 16)
            },
            onNavigateToSettings = {
                scope.launch {
                    kotlinx.coroutines.delay(50)
                    onNavigateToSettings()
                }
            },
            onShowParamSettings = {
                showParamSettingsPanel = true
            },
            themeType = themeType
        )

        // iOS风格展开的相机信息条 - 放在最顶部，渐变边缘
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 100.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 渐变背景层
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                CameraInfoBar(
                    sceneType = sceneType,
                    iso = iso,
                    shutter = shutter,
                    aperture = aperture,
                    modifier = Modifier.fillMaxWidth(),
                    themeType = themeType
                )
            }
        }


        // AI建议气泡 - 放在相机信息条下方
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 140.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (showTip && currentTip.isNotBlank()) {
                AiTipBubble(
                    tip = currentTip,
                    source = currentTipSource,
                    themeType = themeType,
                    modifier = Modifier
                )
            }
        }


        // 相机切换时的遮罩动画
        if (isCameraSwitching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
        }
        
        // 比例切换时的模糊覆盖层 — 使用 Animatable.value 驱动，Compose 自动管理帧调度
        if (blurAnimatable.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = (blurAnimatable.value * 20f).dp)
                    .background(Color.Black.copy(alpha = blurAnimatable.value * 0.2f))
            )
        }

        // 对焦环反馈 — 点击对焦时显示收缩动画环
        focusPoint?.let { point ->
            val ringAlpha = focusRingAnimatable.value
            if (ringAlpha > 0f) {
                val ringRadiusPx = with(density) { (40.dp.toPx() * (0.3f + ringAlpha * 0.7f)) }
                val ringStrokePx = with(density) { 2.5.dp.toPx() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = ringAlpha * 0.8f),
                        radius = ringRadiusPx,
                        center = point,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringStrokePx)
                    )
                }
            }
        }

        // 扇形变焦滑块（展开时显示）
        if (showArcZoom) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // 扇形 UI
                ArcZoomSlider(
                    zoomRatio = zoomRatio,
                    zoomPresets = filteredPresets,
                    zoomRatioRange = zoomRatioRange,
                    onZoomRatioChanged = { newRatio ->
                        // 优化变焦更新：使用防抖和节流
                        camera?.cameraControl?.setZoomRatio(newRatio)
                    },
                    onDismiss = { showArcZoom = false },
                    themeType = themeType,
                    // 使用固定4:3位置，让表盘位置不随比例变化
                    viewfinderBottom = viewfinderBounds43.top + viewfinderBounds43.height,
                    viewfinderWidth = viewfinderBounds43.width
                )
            }
        }


        // 底部控制区域 - 包含变焦按钮、工具栏和拍摄控制（固定位置，不随比例变化）
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 变焦预设按钮（固定在顶部）
            BottomZoomPresets(
                zoomRatio = zoomRatio,
                zoomPresets = filteredPresets,
                onZoomPresetSelected = { preset ->
                    val tid = PerformanceTracer.traceClick("ZoomPreset", "preset=${preset}x")
                    camera?.cameraControl?.setZoomRatio(preset)
                    PerformanceTracer.dump(tid)
                },
                onExpandArcZoom = { showArcZoom = true },
                onCollapseArcZoom = { showArcZoom = false },
                onShowParamSettings = { showParamSettingsPanel = true },
                isArcZoomExpanded = showArcZoom,
                modifier = Modifier.padding(bottom = 24.dp),
                themeType = themeType
            )

            // 五个功能按钮
            SideTools(
                modifier = Modifier.padding(bottom = 24.dp),
                showGuides = showGuides,
                onToggleGuides = {
                    val tid = PerformanceTracer.traceClick("ToggleGuides", "enabled=${!showGuides}")
                    showGuides = !showGuides
                    PerformanceTracer.dump(tid)
                },
                flashEnabled = flashEnabled,
                onToggleFlash = {
                    val tid = PerformanceTracer.traceClick("ToggleFlash", "enabled=${!flashEnabled}")
                    flashEnabled = !flashEnabled
                    PerformanceTracer.dump(tid)
                },
                hdrEnabled = hdrEnabled,
                onToggleHdr = {
                    val tid = PerformanceTracer.traceClick("ToggleHDR", "enabled=${!hdrEnabled}")
                    hdrEnabled = !hdrEnabled
                    CameraBackend.ManualSettings.hdrEnabled = hdrEnabled
                    PerformanceTracer.dump(tid)
                },
                timerSeconds = timerSeconds,
                onCycleTimer = {
                    val next = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
                    val tid = PerformanceTracer.traceClick("CycleTimer", "from=${timerSeconds}s,to=${next}s")
                    timerSeconds = next
                    PerformanceTracer.dump(tid)
                },
                cloudAiEnabled = cloudAiEnabled,
                onToggleCloudAi = {
                    val tid = PerformanceTracer.traceClick("ToggleCloudAI", "enabled=${!cloudAiEnabled}")
                    if (CloudAiService.hasApiKey(context)) {
                        cloudAiEnabled = !cloudAiEnabled
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "请先在设置页配置AI模型API Key",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    PerformanceTracer.dump(tid)
                },
                themeType = themeType
            )

            // 拍摄控制区
            BottomControls(
                modifier = Modifier.fillMaxWidth(),
                iso = iso,
                shutter = shutter,
                aperture = aperture,
                lastPhotoUri = latestPhotoUri,
                isFullscreen = previewAspectRatio < 0f || previewAspectRatio == 0.5625f,
                onFlipCamera = {
                    val tid = PerformanceTracer.traceClick("FlipCamera_BottomBtn")
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                },
                onCapture = {
                    val tid = PerformanceTracer.traceClick("CaptureButton", "timer=${timerSeconds}s")
                    val capture = imageCapture
                    val captureAction = {
                        CameraBackend.capturePhoto(
                            context = context,
                            imageCapture = capture,
                            lensFacing = lensFacing,
                            onSuccess = { path ->
                                lastCapturedUri = android.net.Uri.fromFile(java.io.File(path))
                                PerformanceTracer.dump(tid)
                                onNavigateToEdit(path)
                            },
                            onError = {
                                PerformanceTracer.dump(tid)
                            }
                        )
                    }

                    if (isCountingDown) {
                        // 取消倒计时
                        isCountingDown = false
                        countdownRemaining = 0
                        PerformanceTracer.dump(tid)
                    } else if (timerSeconds > 0) {
                        // 启动倒计时
                        isCountingDown = true
                        countdownRemaining = timerSeconds
                        scope.launch {
                            PerformanceTracer.traceStart("CountdownTimer", tid, "seconds=$timerSeconds")
                            while (countdownRemaining > 0 && isCountingDown) {
                                kotlinx.coroutines.delay(1000L)
                                if (isCountingDown) {
                                    countdownRemaining--
                                }
                            }
                            if (isCountingDown && countdownRemaining == 0) {
                                // 倒计时结束，拍照
                                captureAction()
                            }
                            isCountingDown = false
                        }
                    } else {
                        // 立即拍照
                        captureAction()
                    }
                },
                themeType = themeType
            )
        }

        // 倒计时显示
        if (isCountingDown) {
            CountdownOverlay(
                seconds = countdownRemaining,
                modifier = Modifier.align(Alignment.Center),
                themeType = themeType
            )
        }

        
        // 参数设置面板（独立呼出）
        if (showParamSettingsPanel) {
            ParamSettingsPanel(
                onDismiss = { showParamSettingsPanel = false },
                themeType = themeType
            )
        }



    }
}

