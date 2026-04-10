@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.aicamera.app.ui.screens

import android.Manifest
import android.util.Log
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Divider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.view.Display
import android.view.Surface
import android.hardware.camera2.CaptureRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.FocusMeteringAction
import androidx.compose.ui.platform.LocalConfiguration
import androidx.camera.core.MeteringPoint
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import com.aicamera.app.ui.components.*
import com.aicamera.app.ui.components.TopBarWithActions
import com.aicamera.app.ui.theme.*
import com.aicamera.app.ui.theme.getColorScheme
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import coil.compose.rememberAsyncImagePainter
import com.aicamera.app.backend.ai.AiBackend
import com.aicamera.app.backend.ai.CloudAiService
import com.aicamera.app.backend.ai.CameraSettingsInfo
import com.aicamera.app.backend.camera.CameraBackend
import com.aicamera.app.backend.camera.CameraSession
import com.aicamera.app.backend.camera.CameraAdvancedControls
import androidx.camera.camera2.interop.Camera2Interop
import com.aicamera.app.backend.gallery.GalleryBackend
import com.aicamera.app.backend.gallery.rememberLastPhotoUri
import com.aicamera.app.backend.models.SceneType
import com.aicamera.app.backend.diagnostics.DiagnosticsBackend
import com.aicamera.app.backend.models.FlashMode
import com.aicamera.app.ui.panels.ParamSettingsPanel
// 已移除 AspectRatioPanel 导入
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.sqrt

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

/**
 * AI建议来源枚举
 */
private enum class TipSource {
    NONE,   // 无建议
    CLOUD,  // 云端大模型
    LOCAL   // 本地AI模型
}

//接口
interface PermissionState {
    val isGranted: Boolean
    fun launchMultiplePermissionRequest()
}
//权限函数
@Composable
fun rememberPermissionState(
    permission: String
): PermissionState{
    val context= LocalContext.current
    var isGranted by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) 
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isGranted = granted
    }
    return object : PermissionState{
        override val isGranted: Boolean = isGranted
        override fun launchMultiplePermissionRequest() {
            launcher.launch(permission)
        }
    }
}

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

// 存储布局尺寸，供比例变化时重算 bounds
private data class ScreenInfo(val left: Float, val top: Float, val width: Float, val height: Float)

// 计算相机图像在 PreviewView 中的实际显示区域（FIT_CENTER 逻辑）
private fun computeCameraImageBounds(
    previewViewWidth: Float, previewViewHeight: Float,
    cameraOutputWidth: Int, cameraOutputHeight: Int,
    isFullscreen: Boolean = false
): ViewfinderBounds {
    if (cameraOutputWidth <= 0 || cameraOutputHeight <= 0) return ViewfinderBounds.ZERO

    val camRatio = cameraOutputWidth.toFloat() / cameraOutputHeight
    val pvRatio = if (previewViewHeight > 0f) previewViewWidth / previewViewHeight else 0f

    val (camW, camH, camL, camT) = if (isFullscreen) {
        // 全屏模式：FILL_CENTER，填充整个 PreviewView 并裁剪
        if (camRatio > pvRatio) {
            // 相机图像更宽，以高度为准（左右裁剪）
            val w = previewViewHeight * camRatio
            val h = previewViewHeight
            val l = (previewViewWidth - w) / 2f
            val t = 0f
            listOf(w, h, l, t)
        } else {
            // 相机图像更窄，以宽度为准（上下裁剪）
            val w = previewViewWidth
            val h = previewViewWidth / camRatio
            val l = 0f
            val t = (previewViewHeight - h) / 2f
            listOf(w, h, l, t)
        }
    } else {
        // 非全屏模式：FIT_CENTER，完整显示相机图像
        if (camRatio > pvRatio) {
            // 相机图像更宽，以宽度为准（上下黑边）
            val w = previewViewWidth
            val h = previewViewWidth / camRatio
            val l = 0f
            val t = (previewViewHeight - h) / 2f
            listOf(w, h, l, t)
        } else {
            // 相机图像更窄，以高度为准（左右黑边）
            val w = previewViewHeight * camRatio
            val h = previewViewHeight
            val l = (previewViewWidth - w) / 2f
            val t = 0f
            listOf(w, h, l, t)
        }
    }

    return ViewfinderBounds(
        left = camL,
        top = camT,
        width = camW,
        height = camH
    )
}

// 根据屏幕尺寸和画幅比例计算取景框边界
// aspectRatioPortrait: 竖屏 width/height，-1f 表示全屏
private fun computeViewfinderBounds(
    left: Float, top: Float,
    screenWidth: Float, screenHeight: Float,
    aspectRatioPortrait: Float,
    offsetYPx: Float,
    cameraOutputWidth: Int = 0,
    cameraOutputHeight: Int = 0,
    displayRotation: Int = Surface.ROTATION_0
): ViewfinderBounds {
    if (screenWidth <= 0f || screenHeight <= 0f) return ViewfinderBounds.ZERO

    // 全屏模式：始终填满整个屏幕，忽略相机输出分辨率
    if (aspectRatioPortrait < 0f) {
        return ViewfinderBounds(
            left = 0f,
            top = 0f,
            width = screenWidth,
            height = screenHeight
        )
    }

    // 预览格宽度固定为手机屏幕宽度
    // 预览格高度根据比例计算，确保不超过屏幕高度
    if (aspectRatioPortrait == 1.0f || aspectRatioPortrait == 0.75f || aspectRatioPortrait == 0.5625f) {
        // 对于1:1模式，需要基于相机实际输出比例计算预览区域
        if (aspectRatioPortrait == 1.0f && cameraOutputWidth > 0 && cameraOutputHeight > 0) {
            // 根据显示旋转调整相机输出尺寸
            var outputWidth = cameraOutputWidth
            var outputHeight = cameraOutputHeight
            if (displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180) {
                outputWidth = cameraOutputHeight
                outputHeight = cameraOutputWidth
            }
            
            // 计算相机预览在屏幕上的实际显示区域（FIT_CENTER）
            val cameraBounds = computeCameraImageBounds(
                previewViewWidth = screenWidth,
                previewViewHeight = screenHeight,
                cameraOutputWidth = outputWidth,
                cameraOutputHeight = outputHeight,
                isFullscreen = false
            )
            
            // 在相机预览区域内截取正方形取景框
            val squareSize = minOf(cameraBounds.width, cameraBounds.height)
            val squareLeft = cameraBounds.left + (cameraBounds.width - squareSize) / 2f
            val squareTop = cameraBounds.top + (cameraBounds.height - squareSize) / 2f
            
            return ViewfinderBounds(
                left = squareLeft,
                top = squareTop + offsetYPx,
                width = squareSize,
                height = squareSize
            )
        }
        
        var viewfinderWidth = screenWidth
        var viewfinderHeight = screenWidth / aspectRatioPortrait
        var viewfinderTop = 0f

        // 如果预览格高度超过屏幕高度，等比例缩放
        if (viewfinderHeight > screenHeight) {
            viewfinderHeight = screenHeight
            viewfinderWidth = screenHeight * aspectRatioPortrait
            viewfinderTop = offsetYPx
        } else {
            // 预览格居中
            viewfinderTop = (screenHeight - viewfinderHeight) / 2f + offsetYPx
        }

        return ViewfinderBounds(
            left = 0f,
            top = viewfinderTop,
            width = viewfinderWidth,
            height = viewfinderHeight
        )
    }

    // 非全屏模式：如果有相机输出分辨率，使用相机图像实际显示区域
    if (cameraOutputWidth > 0 && cameraOutputHeight > 0) {
        // 根据显示旋转调整相机输出尺寸
        // 大多数相机传感器是横向的，当显示旋转为0或180度（竖屏）时，需要交换宽高
        var outputWidth = cameraOutputWidth
        var outputHeight = cameraOutputHeight
        if (displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180) {
            // 竖屏模式，交换宽高
            outputWidth = cameraOutputHeight
            outputHeight = cameraOutputWidth
        }

        val cameraBounds = computeCameraImageBounds(
            previewViewWidth = screenWidth,
            previewViewHeight = screenHeight,
            cameraOutputWidth = outputWidth,
            cameraOutputHeight = outputHeight,
            isFullscreen = aspectRatioPortrait == 0.5625f || aspectRatioPortrait < 0f  // 16:9和全屏模式用 FILL_CENTER，4:3、1:1用 FIT_CENTER
        )

        // 应用偏移
        val finalTop = if (aspectRatioPortrait == 0.5625f) {
            // 16:9模式：从屏幕顶部18%位置开始，更居中平衡
            screenHeight * 0.18f + offsetYPx
        } else {
            cameraBounds.top + offsetYPx
        }
        return ViewfinderBounds(
            left = cameraBounds.left,
            top = finalTop,
            width = cameraBounds.width,
            height = cameraBounds.height
        )
    }
    val screenRatio = screenWidth / screenHeight
    var viewfinderLeft: Float
    var viewfinderTop: Float
    var viewfinderWidth: Float
    var viewfinderHeight: Float
    if (screenRatio > aspectRatioPortrait) {
        // 屏幕更宽，以高度为准，左右有黑边
        viewfinderHeight = screenHeight
        viewfinderWidth = screenHeight * aspectRatioPortrait
        viewfinderLeft = (screenWidth - viewfinderWidth) / 2f
        viewfinderTop = 0f
    } else {
        // 屏幕更窄，以宽度为准，上下有黑边
        viewfinderWidth = screenWidth
        viewfinderHeight = screenWidth / aspectRatioPortrait
        viewfinderTop = (screenHeight - viewfinderHeight) / 2f

        if (aspectRatioPortrait == 0.5625f) {
            // 16:9模式：取景框从屏幕顶部18%位置开始，更居中平衡
            viewfinderTop = screenHeight * 0.18f

            // 计算当前取景框底部位置
            val currentBottom = viewfinderTop + viewfinderHeight
            val screenBottom = screenHeight

            // 如果底部超出屏幕，减少高度
            if (currentBottom > screenBottom) {
                val excess = currentBottom - screenBottom
                viewfinderHeight -= excess
            }
        }

        viewfinderLeft = 0f
    }
    return ViewfinderBounds(
        left = viewfinderLeft,
        top = viewfinderTop + offsetYPx,
        width = viewfinderWidth,
        height = viewfinderHeight
    )
}

// 将画幅比例值映射到 CameraX AspectRatio 常量
private fun toCameraAspectRatio(ratioPortrait: Float): Int =
    if (ratioPortrait == 0.5625f || ratioPortrait < 0f) AspectRatio.RATIO_16_9
    else AspectRatio.RATIO_4_3

// 全屏模式使用 FILL_CENTER 填满屏幕，其他模式使用 FIT_CENTER 完整显示
private fun toPreviewScaleType(ratioPortrait: Float): PreviewView.ScaleType =
    if (ratioPortrait < 0f) PreviewView.ScaleType.FILL_CENTER
    else PreviewView.ScaleType.FIT_CENTER

// 创建优化的预览配置，提高清晰度并减少频闪

// 取景框边界数据类
data class ViewfinderBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) {
    companion object {
        val ZERO = ViewfinderBounds()
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
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var previewUseCase: Preview? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    // UI 状态
    var sceneType by remember { mutableStateOf("通用拍摄") }
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
    var extensionsManager: ExtensionsManager? by remember { mutableStateOf(null) }
    // 新增：独立面板显示状态
    var showParamSettingsPanel by remember { mutableStateOf(false) }

    // 相机参数（模拟数据，后端需要从相机获取真实数据）
    var iso by remember { mutableStateOf("Auto") }
    var shutter by remember { mutableStateOf("Auto") }
    var aperture by remember { mutableStateOf("Auto") }

    // AI 建议统一显示（云端优先，本地兜底）
    var currentTip by remember { mutableStateOf("") }
    var showTip by remember { mutableStateOf(false) }
    var currentTipSource by remember { mutableStateOf(TipSource.NONE) } // NONE, CLOUD, LOCAL
    
    // 云端AI建议
    var cloudAiTip by remember { mutableStateOf("") }
    var cloudAiTipPending by remember { mutableStateOf(false) }
    var detectedObjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var cloudAiEnabled by remember { mutableStateOf(CloudAiService.hasApiKey(context)) }
    
    // 本地构图建议
    var compositionTip by remember { mutableStateOf("") }

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
    var isCameraSwitching by remember { mutableStateOf(false) }

    // 轮询比例变化，实时更新 viewfinderBounds
    // 采用PhotonCamera策略：比例切换只调整UI遮罩，不重新绑定相机
    LaunchedEffect(Unit) {
        while (isActive) {
            val newRatio = CameraBackend.ManualSettings.previewAspectRatioPortrait
            if (newRatio != previewAspectRatio) {
                val startTime = System.currentTimeMillis()
                Log.d("CameraScreen", "[比例切换] 开始: $previewAspectRatio -> $newRatio (仅UI调整)")
                
                previewAspectRatio = newRatio
                
                // 同步 PreviewView scaleType
                previewView.scaleType = toPreviewScaleType(newRatio)
                // 所有模式使用统一偏移逻辑，居中显示
                previewOffsetYPx = when {
                    newRatio < 0f -> 0f  // 全屏模式
                    newRatio == 1.0f -> 0f  // 1:1模式
                    else -> 0f  // 其他模式居中
                }
                lastScreenInfo?.let { info ->
                    val cameraOutputWidth = previewUseCase?.resolutionInfo?.resolution?.width ?: 0
                    val cameraOutputHeight = previewUseCase?.resolutionInfo?.resolution?.height ?: 0
                    viewfinderBounds = computeViewfinderBounds(
                        info.left, info.top, info.width, info.height,
                        newRatio, previewOffsetYPx,
                        cameraOutputWidth, cameraOutputHeight,
                        displayRotation
                    )
                    // 固定4:3取景框位置，用于表盘定位
                    viewfinderBounds43 = computeViewfinderBounds(
                        info.left, info.top, info.width, info.height,
                        0.75f, previewOffsetYPx,  // 4:3 = 0.75
                        cameraOutputWidth, cameraOutputHeight,
                        displayRotation
                    )
                    val b = viewfinderBounds
                    val dm = context.resources.displayMetrics
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
                        cameraOutputWidth = cameraOutputWidth,
                        cameraOutputHeight = cameraOutputHeight
                    ))
                }
                
                val elapsed = System.currentTimeMillis() - startTime
                Log.d("CameraScreen", "[比例切换] 完成，耗时: ${elapsed}ms")
            }
            kotlinx.coroutines.delay(200)
        }
    }
    
    // 场景识别（基于 PreviewView.bitmap，避免 ImageProxy 生命周期问题）
    LaunchedEffect(Unit) {
        while (isActive) {
            val bitmap = previewView.bitmap
            if (bitmap != null) {
                try {
                    val result = withContext(Dispatchers.Default) { AiBackend.detectScene(bitmap) }
                    sceneType = when (result.sceneType) {
                        SceneType.PORTRAIT -> "人像拍摄"
                        SceneType.LANDSCAPE -> "风景拍摄"
                        SceneType.FOOD -> "美食拍摄"
                        SceneType.NIGHT -> "夜景拍摄"
                        SceneType.ARCHITECTURE -> "建筑拍摄"
                        SceneType.AUTO -> "通用拍摄"
                    }
                    detectedObjects = result.detectedObjects
                } finally {
                    // 释放bitmap资源
                    bitmap.recycle()
                }
            }
            kotlinx.coroutines.delay(1000) // 增加延迟，减少CPU占用
        }
    }
    
    // 云端AI分析（低频调用，节省API费用）- 云端优先
    LaunchedEffect(showGuides, cloudAiEnabled) {
        if (!showGuides || !cloudAiEnabled) return@LaunchedEffect
        while (isActive) {
            if (!CloudAiService.hasApiKey(context)) {
                Log.d("CameraScreen", "[AI建议] API Key未配置，跳过云端分析")
                kotlinx.coroutines.delay(5000)
                continue
            }
            kotlinx.coroutines.delay(8000) // 增加延迟，减少频率
            val bitmap = previewView.bitmap ?: continue
            try {
                val settings = CameraSettingsInfo(
                    iso = if (iso != "Auto") iso.toIntOrNull() else null,
                    shutterSpeed = shutter,
                    ev = CameraBackend.ManualSettings.evIndex
                )
                
                cloudAiTipPending = true
                val result = CloudAiService.analyzeScene(context, bitmap, detectedObjects, settings)
                cloudAiTipPending = false
                
                if (result.success && result.suggestions.isNotEmpty()) {
                    cloudAiTip = result.suggestions.first()
                    // 云端建议优先级高，覆盖本地建议
                    currentTip = cloudAiTip
                    currentTipSource = TipSource.CLOUD
                    showTip = true
                    Log.i("CameraScreen", "[AI建议] 显示云端建议: $currentTip")
                    kotlinx.coroutines.delay(4000)
                    // 只有当前显示的仍是这条云端建议时才隐藏
                    if (currentTipSource == TipSource.CLOUD && currentTip == cloudAiTip) {
                        showTip = false
                        currentTipSource = TipSource.NONE
                    }
                } else {
                    // 云端调用失败，记录错误
                    Log.w("CameraScreen", "[AI建议] 云端模型调用失败: ${result.errorMessage}，将使用本地模型兜底")
                }
            } finally {
                // 释放bitmap资源
                bitmap.recycle()
            }
        }
    }

    // 构图分析（本地AI兜底，仅在云端无建议时显示）
    LaunchedEffect(showGuides) {
        if (!showGuides) return@LaunchedEffect
        while (isActive) {
            kotlinx.coroutines.delay(3000) // 增加延迟，减少频率
            
            // 云端建议正在处理或已显示时，跳过本地建议
            if (cloudAiTipPending || currentTipSource == TipSource.CLOUD) {
                continue
            }
            
            val bitmap = previewView.bitmap ?: continue
            try {
                val st = when (sceneType) {
                    "人像拍摄" -> SceneType.PORTRAIT
                    "风景拍摄" -> SceneType.LANDSCAPE
                    "美食拍摄" -> SceneType.FOOD
                    "夜景拍摄" -> SceneType.NIGHT
                    "建筑拍摄" -> SceneType.ARCHITECTURE
                    else -> SceneType.AUTO
                }
                val result = withContext(Dispatchers.Default) { AiBackend.analyzeComposition(bitmap, st) }
                val suggestion = result.suggestions.firstOrNull()
                if (result.success && suggestion != null && suggestion.message.isNotBlank()) {
                    compositionTip = suggestion.message
                    // 只在无云端建议时显示本地建议
                    if (currentTipSource != TipSource.CLOUD) {
                        currentTip = compositionTip
                        currentTipSource = TipSource.LOCAL
                        showTip = true
                        Log.i("CameraScreen", "[AI建议] 显示本地建议: $currentTip")
                        kotlinx.coroutines.delay(3000)
                        // 只有当前显示的仍是这条本地建议时才隐藏
                        if (currentTipSource == TipSource.LOCAL && currentTip == compositionTip) {
                            showTip = false
                            currentTipSource = TipSource.NONE
                        }
                    }
                }
            } finally {
                // 释放bitmap资源
                bitmap.recycle()
            }
        }
    }

    // 初始化相机
        LaunchedEffect(Unit) {
            val provider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider = provider

            // 按当前比例设置相机输出分辨率比例，使用高分辨率提升清晰度
            val preview = Preview.Builder()
                .setTargetRotation(displayRotation)
                .setTargetAspectRatio(cameraTargetAspectRatio)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            previewUseCase = preview

        // 拍照使用默认配置
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
            .setTargetRotation(displayRotation)
            .build()
        imageCapture = capture

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            CameraSession.set(camera, capture)

            // 配置相机参数以优化画质和减少频闪
            try {
                val currentCamera = camera ?: throw IllegalStateException("相机未绑定")
                val camera2Control = androidx.camera.camera2.interop.Camera2CameraControl.from(currentCamera.cameraControl)
                val options = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
                    )
                    .setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    .build()
                camera2Control.setCaptureRequestOptions(options)
            } catch (e: Throwable) {
                Log.e("CameraScreen", "无法配置相机优化参数", e)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 延迟初始化extensionsManager，不影响相机预览启动
        lifecycleScope.launch {
            extensionsManager = try {
                ExtensionsManager.getInstanceAsync(context, provider).get()
            } catch (_: Throwable) {
                null
            }
        }
    }

    // HDR / 翻转摄像头时重新绑定相机
    // 注意：比例切换不再触发相机重绑定，采用PhotonCamera的策略，只调整UI遮罩
    var lastLensFacing by remember { mutableStateOf(lensFacing) }
    var lastHdrEnabled by remember { mutableStateOf(hdrEnabled) }

    LaunchedEffect(hdrEnabled, lensFacing, cameraProvider, imageCapture, extensionsManager) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val capture = imageCapture ?: return@LaunchedEffect

        // 只有在摄像头切换或HDR切换时才重新绑定相机
        val needRebind = lensFacing != lastLensFacing || hdrEnabled != lastHdrEnabled
        if (!needRebind) return@LaunchedEffect
        
        Log.d("CameraScreen", "[相机重绑定] 开始: lensFacing=$lensFacing, hdrEnabled=$hdrEnabled")
        val rebindStart = System.currentTimeMillis()
        isCameraSwitching = true
        lastLensFacing = lensFacing
        lastHdrEnabled = hdrEnabled

        val baseSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val selector = if (hdrEnabled) {
            val mgr = extensionsManager
            if (mgr != null && mgr.isExtensionAvailable(baseSelector, ExtensionMode.HDR)) {
                mgr.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
            } else {
                baseSelector
            }
        } else {
            baseSelector
        }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase!!, capture)
            CameraSession.set(camera, capture)

            isCameraSwitching = false

            camera?.cameraControl?.setZoomRatio(0.5f)

            // 配置相机参数以优化画质和减少频闪
            try {
                val currentCamera = camera ?: throw IllegalStateException("相机未绑定")
                val camera2Control = androidx.camera.camera2.interop.Camera2CameraControl.from(currentCamera.cameraControl)
                val options = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
                    )
                    .setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                        android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    .setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                    )
                    .build()
                camera2Control.setCaptureRequestOptions(options)
                Log.d("CameraScreen", "相机优化参数已配置：抗频闪、连续对焦、光学防抖")
            } catch (e: Throwable) {
                Log.e("CameraScreen", "无法配置相机优化参数", e)
            }
            
            val rebindElapsed = System.currentTimeMillis() - rebindStart
            Log.d("CameraScreen", "[相机重绑定] 完成，耗时: ${rebindElapsed}ms")
        } catch (e: Throwable) {
            Log.e("CameraScreen", "[相机重绑定] 失败", e)
        }
    }

    // 同步HDR设置
    LaunchedEffect(Unit) {
        while (isActive) {
            if (CameraBackend.ManualSettings.hdrEnabled != hdrEnabled) {
                hdrEnabled = CameraBackend.ManualSettings.hdrEnabled
            }
            kotlinx.coroutines.delay(500) // 增加延迟，减少CPU占用
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
            // 更新变焦状态
            c.cameraInfo.zoomState.value?.let { zoomState ->
                zoomLinear = zoomState.linearZoom
                zoomRatio = zoomState.zoomRatio
                zoomRatioRange = zoomState.minZoomRatio..zoomState.maxZoomRatio
            }
            kotlinx.coroutines.delay(500) // 增加延迟，减少CPU占用
        }
    }

    // 翻转摄像头函数（供手势和底部控制区使用）
    fun onFlipCamera() {
        val provider = cameraProvider
        val preview = previewUseCase
        val capture = imageCapture
        if (provider == null || preview == null || capture == null) return
        try {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            // 更新预览镜像设置
            previewView.scaleX = if (lensFacing == CameraSelector.LENS_FACING_FRONT) -1f else 1f
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)

            CameraSession.set(camera, capture)
        } catch (_: Throwable) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(camera, previewView, context, latestPhotoUri) {
                // 双击切换摄像头
                detectTapGestures(
                    onDoubleTap = { onFlipCamera() },
                    onTap = { tap ->
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
                        scope.launch {
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
            ViewfinderMask(viewfinderBounds, themeType)
        }

        // 构图辅助线 - 基于实际预览区域绘制，与取景框同步偏移
        if (showGuides && viewfinderBounds.width > 0) {
            CompositionGuides(viewfinderBounds, themeType)

        }

        // 顶部栏 - 相机信息和设置按钮同行
        TopBar(
            sceneType = sceneType,
            iso = iso,
            shutter = shutter,
            previewAspectRatio = previewAspectRatio,
            onAspectRatioChanged = { newRatio ->
                Log.d("CameraScreen", "画幅比例选择: $newRatio")
                previewAspectRatio = newRatio
                CameraBackend.ManualSettings.previewAspectRatioPortrait = newRatio
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            themeType = themeType
        )

        // iOS风格展开的相机信息条 - 放在最顶部，渐变边缘
        Box(
            modifier = Modifier.fillMaxWidth(),
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
                .padding(top = 100.dp),
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
                    camera?.cameraControl?.setZoomRatio(preset)
                    // 不在这里关闭扇形，让滑动手势可以持续操作
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
                showGuides = showGuides,
                onToggleGuides = { showGuides = !showGuides },
                flashEnabled = flashEnabled,
                onToggleFlash = { flashEnabled = !flashEnabled },
                hdrEnabled = hdrEnabled,
                onToggleHdr = {
                    hdrEnabled = !hdrEnabled
                    CameraBackend.ManualSettings.hdrEnabled = hdrEnabled
                },
                timerSeconds = timerSeconds,
                onCycleTimer = {
                    timerSeconds = when (timerSeconds) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                },
                cloudAiEnabled = cloudAiEnabled,
                onToggleCloudAi = {
                    if (CloudAiService.hasApiKey(context)) {
                        cloudAiEnabled = !cloudAiEnabled
                    }
                },
                modifier = Modifier.padding(bottom = 24.dp),
                themeType = themeType
            )

            // 拍摄控制区
            BottomControls(
                iso = iso,
                shutter = shutter,
                aperture = aperture,
                lastPhotoUri = latestPhotoUri,
                isFullscreen = previewAspectRatio < 0f || previewAspectRatio == 0.5625f,
                onFlipCamera = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK },
                onCapture = {
                    val capture = imageCapture
                    val captureAction = {
                        CameraBackend.capturePhoto(
                            context = context,
                            imageCapture = capture,
                            lensFacing = lensFacing,
                            onSuccess = { path ->
                                lastCapturedUri = android.net.Uri.fromFile(java.io.File(path))
                                onNavigateToEdit(path)
                            },
                            onError = { /* TODO: show toast/snackbar */ }
                        )
                    }

                    if (isCountingDown) {
                        // 取消倒计时
                        isCountingDown = false
                        countdownRemaining = 0
                    } else if (timerSeconds > 0) {
                        // 启动倒计时
                        isCountingDown = true
                        countdownRemaining = timerSeconds
                        scope.launch {
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
                modifier = Modifier.fillMaxWidth(),
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

/**
 * 顶部栏组件 - 直接显示画幅比例按钮
 */
@Composable
private fun TopBar(
    sceneType: String,
    iso: String,
    shutter: String,
    previewAspectRatio: Float,
    onAspectRatioChanged: (Float) -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowParamSettings: () -> Unit,
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：占位
        Spacer(modifier = Modifier.width(40.dp))

        // 右侧：设置按钮
        CircleIconButton(
            icon = Icons.Default.Settings,
            onClick = onNavigateToSettings,
            // 浅色主题：无底色，白色图标
            backgroundColor = Color.Transparent,
            iconTint = if (themeType == ThemeType.FRESH) Color.White else colorScheme.onBackground,
            contentDescription = "设置",
            size = 40.dp,
            iconSize = 22.dp
        )
    }
}

/**
 * 右侧工具栏
 */
@Composable
private fun SideTools(
    showGuides: Boolean,
    onToggleGuides: () -> Unit,
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    hdrEnabled: Boolean,
    onToggleHdr: () -> Unit,
    timerSeconds: Int,
    onCycleTimer: () -> Unit,
    cloudAiEnabled: Boolean,
    onToggleCloudAi: () -> Unit,
    modifier: Modifier = Modifier,
    themeType: ThemeType,
    showGradientBg: Boolean = true  // 是否显示渐变背景
) {
    val colorScheme = getColorScheme(themeType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 闪光灯（移到最左边）
        CircleIconButton(
            icon = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
            onClick = onToggleFlash,
            backgroundColor = when {
                themeType == ThemeType.FRESH && flashEnabled -> colorScheme.primary
                themeType == ThemeType.FRESH -> Color.White
                else -> Color.Transparent
            },
            iconTint = when {
                flashEnabled -> if (themeType == ThemeType.FRESH) Color.White else colorScheme.primary
                themeType == ThemeType.FRESH -> colorScheme.onSurface
                else -> Color.White
            },
            contentDescription = "闪光灯",
            size = 40.dp,
            iconSize = 20.dp,
            borderColor = if (themeType == ThemeType.FRESH && !flashEnabled) Color.White.copy(alpha = 0.3f) else null,
            useGradient = themeType == ThemeType.FRESH && !flashEnabled
        )

        // 辅助线开关
        CircleIconButton(
            icon = if (showGuides) Icons.Default.GridOn else Icons.Default.GridOff,
            onClick = onToggleGuides,
            backgroundColor = when {
                themeType == ThemeType.FRESH && showGuides -> colorScheme.primary
                themeType == ThemeType.FRESH -> Color.White
                else -> Color.Transparent
            },
            borderColor = null,
            iconTint = when {
                showGuides -> if (themeType == ThemeType.FRESH) Color.White else colorScheme.primary
                themeType == ThemeType.FRESH -> colorScheme.onSurface
                else -> Color.White
            },
            contentDescription = "辅助线",
            size = 40.dp,
            iconSize = 20.dp,
            useGradient = themeType == ThemeType.FRESH && !showGuides
        )

        // HDR - 显示文字
        CircleIconButton(
            text = "HDR",
            onClick = onToggleHdr,
            backgroundColor = when {
                themeType == ThemeType.FRESH && hdrEnabled -> colorScheme.primary
                themeType == ThemeType.FRESH -> Color.White
                else -> Color.Transparent
            },
            iconTint = when {
                hdrEnabled -> if (themeType == ThemeType.FRESH) Color.White else colorScheme.primary
                themeType == ThemeType.FRESH -> colorScheme.onSurface
                else -> Color.White
            },
            contentDescription = "HDR",
            size = 40.dp,
            iconSize = 20.dp,
            borderColor = null,
            useGradient = themeType == ThemeType.FRESH && !hdrEnabled
        )

        // 云端AI辅助 - 统一使用CircleIconButton
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircleIconButton(
                icon = Icons.Default.AutoAwesome,
                onClick = onToggleCloudAi,
                backgroundColor = when {
                    themeType == ThemeType.FRESH && cloudAiEnabled -> colorScheme.primary
                    themeType == ThemeType.FRESH -> Color.White
                    else -> Color.Transparent
                },
                iconTint = when {
                    cloudAiEnabled -> if (themeType == ThemeType.FRESH) Color.White else colorScheme.primary
                    themeType == ThemeType.FRESH -> colorScheme.onSurface
                    else -> Color.White
                },
                contentDescription = "云端AI",
                size = 40.dp,
                iconSize = 20.dp,
                useGradient = themeType == ThemeType.FRESH && !cloudAiEnabled
            )
            Text(
                text = if (cloudAiEnabled) "on" else "off",
                fontSize = 8.sp,
                color = if (cloudAiEnabled) colorScheme.primary else colorScheme.onSurfaceVariant
            )
        }

        // 定时器 - 统一使用CircleIconButton
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircleIconButton(
                icon = Icons.Default.Timer,
                onClick = onCycleTimer,
                backgroundColor = when {
                    themeType == ThemeType.FRESH && timerSeconds > 0 -> colorScheme.primary
                    themeType == ThemeType.FRESH -> Color.White
                    else -> Color.Transparent
                },
                iconTint = when {
                    timerSeconds > 0 -> if (themeType == ThemeType.FRESH) Color.White else colorScheme.primary
                    themeType == ThemeType.FRESH -> colorScheme.onSurface
                    else -> Color.White
                },
                contentDescription = "定时器",
                size = 40.dp,
                iconSize = 20.dp,
                useGradient = themeType == ThemeType.FRESH && timerSeconds == 0
            )
            Text(
                text = when (timerSeconds) {
                    0 -> "off"
                    3 -> "3s"
                    10 -> "10s"
                    else -> "off"
                },
                fontSize = 8.sp,
                color = if (timerSeconds > 0) colorScheme.primary else colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * 底部控制区 - iOS风格布局 [相册][拍摄][反转摄像头]
 */
@Composable
private fun BottomControls(
    iso: String,
    shutter: String,
    aperture: String,
    lastPhotoUri: android.net.Uri?,
    onCapture: () -> Unit,
    onFlipCamera: () -> Unit,
    isFullscreen: Boolean = false,
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val context = LocalContext.current
    val colorScheme = getColorScheme(themeType)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 主控制行 - iOS风格：相册 - 拍摄按钮 - 反转摄像头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：相册缩略图
            GalleryThumbnailButton(
                photoUri = lastPhotoUri,
                onClick = {
                    GalleryBackend.openGallery(context, lastPhotoUri)
                },
                size = 72.dp
            )

            // 中央：拍摄按钮
            CaptureButton(
                onClick = onCapture,
                size = 72.dp
            )

            // 右侧：反转摄像头按钮
            IconButton(
                onClick = onFlipCamera,
                modifier = Modifier.size(88.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "反转摄像头",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}


/**
 * 取景框遮罩 - 在预览画面上叠加黑色半透明遮罩，只露出取景框区域
 * 不绘制边框
 */
@Composable
private fun ViewfinderMask(bounds: ViewfinderBounds, themeType: ThemeType) {
    val colorScheme = getColorScheme(themeType)

    Canvas(modifier = Modifier.fillMaxSize()) {
        // 预览框外区域绘制60%透明黑边
        val maskAlpha = 0.6f
        val maskColor = Color.Black.copy(alpha = maskAlpha)
        val bottomY = bounds.top + bounds.height

        // 顶部遮罩
        if (bounds.top > 0) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, bounds.top)
            )
        }

        // 底部遮罩
        if (bottomY < size.height) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, bottomY),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - bottomY)
            )
        }

        // 左右遮罩
        val rightX = bounds.left + bounds.width
        if (bounds.left > 0) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, bounds.top),
                size = androidx.compose.ui.geometry.Size(bounds.left, bounds.height)
            )
        }
        if (rightX < size.width) {
            drawRect(
                color = maskColor,
                topLeft = Offset(rightX, bounds.top),
                size = androidx.compose.ui.geometry.Size(size.width - rightX, bounds.height)
            )
        }

        // 已移除边框绘制
    }
}

/**
 * 构图辅助线（三分法）- 基于实际取景框区域动态适配
 *
 * 实现思路：
 * 1. 根据 PreviewView 的布局位置和实际预览图像比例，计算取景框在屏幕中的实际显示区域（ViewfinderBounds）
 * 2. 取景框区域已经考虑了 FIT_CENTER 或 FILL_CENTER 缩放模式下的黑边或裁剪区域
 * 3. 九宫格线严格限制在取景框内部：垂直线和水平线的起点和终点均以取景框边界为限
 * 4. 三等分计算：将取景框宽度和高度分别除以3，得到1/3和2/3位置，确保等比例划分
 *
 * 坐标计算：
 * - 取景框左上角坐标：(bounds.left, bounds.top)
 * - 取景框宽度：bounds.width，高度：bounds.height
 * - 垂直线位置：x1 = bounds.left + bounds.width / 3, x2 = bounds.left + bounds.width * 2 / 3
 * - 水平线位置：y1 = bounds.top + bounds.height / 3, y2 = bounds.top + bounds.height * 2 / 3
 * - 线条绘制范围：垂直线从 (x, bounds.top) 到 (x, bounds.top + bounds.height)
 *               水平线从 (bounds.left, y) 到 (bounds.left + bounds.width, y)
 */
@Composable
private fun CompositionGuides(bounds: ViewfinderBounds, themeType: ThemeType) {
    val colorScheme = getColorScheme(themeType)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val lineColor = if (themeType == ThemeType.FRESH) Color.White.copy(alpha = 0.6f) else colorScheme.primary.copy(alpha = 0.5f)
        val strokeWidth = 2f

        // 垂直线（1/3 和 2/3 位置）- 基于实际取景框宽度
        val verticalLine1X = bounds.left + bounds.width / 3
        val verticalLine2X = bounds.left + bounds.width * 2 / 3

        drawLine(
            color = lineColor,
            start = Offset(verticalLine1X, bounds.top),
            end = Offset(verticalLine1X, bounds.top + bounds.height),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = lineColor,
            start = Offset(verticalLine2X, bounds.top),
            end = Offset(verticalLine2X, bounds.top + bounds.height),
            strokeWidth = strokeWidth
        )

        // 水平线（1/3 和 2/3 位置）- 基于实际取景框高度
        val horizontalLine1Y = bounds.top + bounds.height / 3
        val horizontalLine2Y = bounds.top + bounds.height * 2 / 3

        drawLine(
            color = lineColor,
            start = Offset(bounds.left, horizontalLine1Y),
            end = Offset(bounds.left + bounds.width, horizontalLine1Y),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = lineColor,
            start = Offset(bounds.left, horizontalLine2Y),
            end = Offset(bounds.left + bounds.width, horizontalLine2Y),
            strokeWidth = strokeWidth
        )

    }
}

/**
 * 权限请求界面
 */
@Composable
private fun PermissionRequestScreen(
    themeType: ThemeType,
    onRequestPermission: () -> Unit
) {
    val colorScheme = getColorScheme(themeType)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            CircleIconButton(
                icon = Icons.Default.CameraAlt,
                onClick = {},
                size = 80.dp,
                iconSize = 40.dp,
                backgroundColor = colorScheme.surface
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = "授予相机权限",
                onClick = onRequestPermission,
                icon = Icons.Default.Camera,
                themeType = themeType
            )
        }
    }
}
/**
 * 统一的AI建议提示气泡
 *
 * 用于显示云端或本地AI的构图建议
 * 云端建议：紫色主题 + 云图标
 * 本地建议：绿色主题 + 灯泡图标
 */
@Composable
private fun AiTipBubble(
    tip: String,
    source: TipSource,
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)
    // 根据主题类型选择合适的渐变色
    val gradientColors = when (source) {
        TipSource.CLOUD -> if (themeType == ThemeType.FRESH) {
            listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)) // 浅色：深紫色
        } else {
            listOf(colorScheme.tertiary, colorScheme.tertiary.copy(alpha = 0.7f)) // 暗色：主题色
        }
        TipSource.LOCAL -> if (themeType == ThemeType.FRESH) {
            listOf(Color(0xFF10B981), Color(0xFF059669)) // 浅色：深绿色
        } else {
            listOf(colorScheme.primary, colorScheme.primary.copy(alpha = 0.7f)) // 暗色：主题色
        }
        TipSource.NONE -> if (themeType == ThemeType.FRESH) {
            listOf(Color(0xFF6B7280), Color(0xFF4B5563)) // 浅色：灰色
        } else {
            listOf(Color(0xFF6B7280), Color(0xFF4B5563)) // 暗色：灰色
        }
    }
    val icon = when (source) {
        TipSource.CLOUD -> Icons.Default.Cloud
        else -> Icons.Default.Lightbulb
    }

    Box(modifier = modifier) {
        // 气泡主体 - 带渐变
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(gradientColors),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                androidx.compose.material3.Text(
                    text = tip,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.widthIn(max = 260.dp)
                )
            }
        }

        // 气泡小尾巴
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .graphicsLayer {
                    rotationZ = 45f
                }
                .size(12.dp)
                .background(
                    brush = Brush.horizontalGradient(gradientColors),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * 倒计时覆盖层
 */
@Composable
private fun CountdownOverlay(
    seconds: Int,
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(colorScheme.surface.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = seconds.toString(),
            fontSize = 48.sp,
            color = colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 底部变焦预设按钮（苹果风格）- 带参数设置按钮
 */
@Composable
private fun BottomZoomPresets(
    zoomRatio: Float,
    zoomPresets: List<Float>,
    onZoomPresetSelected: (Float) -> Unit,
    onExpandArcZoom: () -> Unit,
    onCollapseArcZoom: () -> Unit,
    onShowParamSettings: () -> Unit,
    isArcZoomExpanded: Boolean,
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)
    // 找到最接近的预设值
    val closestPreset = zoomPresets.minByOrNull { Math.abs(it - zoomRatio) } ?: 1.0f
    val isSelected = (zoomRatio - closestPreset).absoluteValue < 0.1f

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 居中放置：变焦按钮
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(
                    width = 1.dp,
                    color = when {
                        isSelected -> colorScheme.primary
                        themeType == ThemeType.FRESH -> Color.Transparent
                        themeType == ThemeType.TECH -> Color(0xFF00D4AA)
                        else -> Color(0xFFFF9500)
                    },
                    shape = CircleShape
                )
                .clickable {
                    if (isSelected) {
                        // 点击当前选中的预设，展开扇形变焦
                        onExpandArcZoom()
                    } else {
                        // 点击其他预设，直接切换到该变焦值，然后关闭扇形
                        onZoomPresetSelected(closestPreset)
                        onCollapseArcZoom()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${"%.1f".format(zoomRatio)}x",
                fontSize = 12.sp,
                color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        // 最右侧：参数设置按钮
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(colorScheme.surface.copy(alpha = 0.5f))
                .clickable { onShowParamSettings() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "参数设置",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 扇形变焦滑块 - 底边对齐预览框底部，宽度为屏幕宽度
 */
@Composable
private fun ArcZoomSlider(
    zoomRatio: Float,
    zoomPresets: List<Float>,
    zoomRatioRange: ClosedFloatingPointRange<Float>,
    onZoomRatioChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    themeType: ThemeType,
    viewfinderBottom: Float = 0f,
    viewfinderWidth: Float = 0f,
    modifier: Modifier = Modifier
) {
    val colorScheme = getColorScheme(themeType)
    val filteredPresets = zoomPresets.filter { it in zoomRatioRange }

    fun zoomToAngle(zoom: Float): Float {
        val normalized = (zoom - zoomRatioRange.start) / (zoomRatioRange.endInclusive - zoomRatioRange.start)
        return 180f + normalized * 180f
    }

    fun angleToZoom(angle: Float): Float {
        val normalized = (angle - 180f) / 180f
        return zoomRatioRange.start + normalized * (zoomRatioRange.endInclusive - zoomRatioRange.start)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // 扇形区域触摸检测层 - 只处理扇形区域内的拖拽，不阻止外层点击
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoomRatio, zoomRatioRange, viewfinderBottom) {
                    // 预计算布局参数
                    val layoutSize = size
                    val screenW = layoutSize.width
                    val radius = screenW / 2
                    val bottomY = if (viewfinderBottom > 0) viewfinderBottom else layoutSize.height * 0.65f
                    val centerX = screenW / 2
                    val centerY = bottomY

                    // 触摸状态
                    var isDragging = false
                    var lastSentZoom = zoomRatio
                    var lastUpdateTime = 0L
                    val frameTime = 33L // 约30fps，减少相机API调用频率

                    detectDragGestures(
                        onDragStart = { offset ->
                            val dx = offset.x - centerX
                            val dy = offset.y - centerY
                            val squaredDistance = dx * dx + dy * dy

                            // 检查是否在扇形区域内开始拖拽
                            if (squaredDistance <= radius * radius && offset.y <= centerY) {
                                isDragging = true

                                // 处理初始触摸点
                                var angle = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
                                if (angle < 0) angle += 360f
                                if (angle >= 0f && angle <= 180f) angle += 180f

                                if (angle >= 180f && angle <= 360f) {
                                    val newZoom = angleToZoom(angle)
                                    val clampedZoom = newZoom.coerceIn(zoomRatioRange.start, zoomRatioRange.endInclusive)
                                    onZoomRatioChanged(clampedZoom)
                                    lastSentZoom = clampedZoom
                                    lastUpdateTime = System.currentTimeMillis()
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (!isDragging) return@detectDragGestures

                            change.consume()
                            val currentTime = System.currentTimeMillis()

                            // 节流处理
                            if (currentTime - lastUpdateTime < frameTime) {
                                return@detectDragGestures
                            }

                            val currentPosition = change.position
                            val currentDx = currentPosition.x - centerX
                            val currentDy = currentPosition.y - centerY

                            // 检查是否仍在扇形区域内
                            val currentSquaredDist = currentDx * currentDx + currentDy * currentDy
                            if (currentSquaredDist <= radius * radius * 1.5f && currentPosition.y <= centerY * 1.1f) {
                                // 允许稍微超出边界，提供更好的用户体验
                                var currentAngle = (atan2(currentDy.toDouble(), currentDx.toDouble()) * 180.0 / PI).toFloat()
                                if (currentAngle < 0) currentAngle += 360f
                                if (currentAngle >= 0f && currentAngle <= 180f) currentAngle += 180f

                                // 限制角度范围
                                currentAngle = maxOf(180f, minOf(360f, currentAngle))

                                val newZoom = angleToZoom(currentAngle)
                                val clampedZoom = newZoom.coerceIn(zoomRatioRange.start, zoomRatioRange.endInclusive)

                                // 只有当变焦值有显著变化时才更新
                                if (abs(clampedZoom - lastSentZoom) > 0.08f) {
                                    onZoomRatioChanged(clampedZoom)
                                    lastSentZoom = clampedZoom
                                    lastUpdateTime = currentTime
                                }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        )

        // 扇形变焦表盘 - 固定版
// 表盘固定在viewfinder底部，刻度永远完整显示
// 水平滑动改变表盘上的有效位置，但表盘本身不旋转
// 半圆下边和屏幕宽度一致

        // 主题色：TECH用青绿色，PROFESSIONAL用橙色，FRESH用黑灰色
        val arcColor1 = when (themeType) {
            ThemeType.TECH -> Color(0xFF00E5FF)
            ThemeType.PROFESSIONAL -> Color(0xFFFF9500)
            ThemeType.FRESH -> Color(0xFF000000)
        }
        val arcColor2 = when (themeType) {
            ThemeType.TECH -> Color(0xFF00FF88)
            ThemeType.PROFESSIONAL -> Color(0xFFFF6B00)
            ThemeType.FRESH -> Color(0xFF333333)
        }

        // 获取屏幕尺寸
        val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
        val arcRadius = screenWidthDp / 2 - 16.dp
        val arcRadiusPx = with(LocalDensity.current) { arcRadius.toPx() }
        val centerX = with(LocalDensity.current) { (screenWidthDp / 2).toPx() }
        val bottomY = viewfinderBottom

        // Canvas绘制表盘
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = arcRadiusPx
            val cx = centerX
            val cy = bottomY
            val strokeWidth = 3.dp.toPx()

            // 计算当前zoom对应的角度
            val currentAngle = zoomToAngle(zoomRatio)
            val sweepAngle = currentAngle - 180f

            // 绘制背景半圆弧（灰色底）
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth * 1.5f)
            )

            // 绘制渐变填充的进度弧（从1x到当前zoom）
            if (sweepAngle > 0f) {
                // 主进度弧（渐变色）
                drawArc(
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                        colors = listOf(
                            arcColor1,
                            arcColor2,
                            arcColor1.copy(alpha = 0.8f),
                            arcColor2,
                            arcColor1
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 180f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }

            // 绘制密集尺子刻度（每3°一个，共60个）
            for (i in 0..60) {
                val angle = 180f + i * 3f
                if (angle > 360f) break

                val rad = (angle * PI.toFloat() / 180f).toDouble()
                // 主刻度(0,10,20...)最长，中等刻度(5,15,25...)中长，细刻最短
                val isMajor = (i % 10 == 0)    // 每30°一个主刻度 (1x,2x,3x,4x,5x,6x)
                val isMedium = (i % 5 == 0)    // 每15°一个中刻度
                val innerR = when {
                    isMajor -> radius * 0.78f
                    isMedium -> radius * 0.82f
                    else -> radius * 0.88f
                }
                val outerR = radius * 0.95f

                val x1 = cx + innerR * cos(rad).toFloat()
                val y1 = cy + innerR * sin(rad).toFloat()
                val x2 = cx + outerR * cos(rad).toFloat()
                val y2 = cy + outerR * sin(rad).toFloat()

                val tickColor = when {
                    angle <= currentAngle && isMajor -> arcColor1
                    angle <= currentAngle && isMedium -> arcColor1.copy(alpha = 0.9f)
                    angle <= currentAngle -> arcColor1.copy(alpha = 0.7f)
                    isMajor -> Color.White.copy(alpha = 1.0f)
                    isMedium -> Color.White.copy(alpha = 0.8f)
                    else -> Color.White.copy(alpha = 0.6f)
                }

                val strokeW = when {
                    isMajor -> 2.5f
                    isMedium -> 1.5f
                    else -> 1f
                }

                drawLine(
                    color = tickColor,
                    start = Offset(x1, y1), end = Offset(x2, y2),
                    strokeWidth = strokeW
                )
            }

            // 绘制底线
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(0f, bottomY),
                end = Offset(size.width, bottomY),
                strokeWidth = strokeWidth
            )

            // 绘制当前变焦位置指示器（带发光效果）
            val indicatorRad = (currentAngle * PI.toFloat() / 180f).toDouble()
            val indicatorR = radius * 0.75f
            val ix = cx + indicatorR * cos(indicatorRad).toFloat()
            val iy = cy + indicatorR * sin(indicatorRad).toFloat()

            // 外层发光 - 适配主题色
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        arcColor2.copy(alpha = 0.6f),
                        arcColor2.copy(alpha = 0f)
                    ),
                    center = Offset(ix, iy),
                    radius = 20.dp.toPx()
                ),
                radius = 20.dp.toPx(),
                center = Offset(ix, iy)
            )
            // 主圆点 - 适配主题色
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        arcColor2
                    ),
                    center = Offset(ix, iy),
                    radius = 10.dp.toPx()
                ),
                radius = 10.dp.toPx(),
                center = Offset(ix, iy)
            )
            // 中心白点
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(ix, iy))
        }

        // 刻度标签（固定位置）
        val labels = listOf(
            180f to "1x",
            216f to "2x",
            252f to "3x",
            288f to "4x",
            324f to "5x",
            360f to "6x"
        )

        for ((angle, label) in labels) {
            val rad = (angle * PI.toFloat() / 180f).toDouble()
            val textR = arcRadiusPx * 0.65f
            val tx = centerX + textR * cos(rad).toFloat()
            val ty = bottomY + textR * sin(rad).toFloat()

            Text(
                text = label,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .offset(
                        x = with(LocalDensity.current) { (tx - 12).toDp() },
                        y = with(LocalDensity.current) { (ty - 8).toDp() }
                    )
            )
        }

        // 正北位置（半圆正上方）- 显示当前倍率（带渐变背景）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = with(LocalDensity.current) { (bottomY - arcRadiusPx * 0.7f).toDp() })
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF00FF88)
                            )
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${"%.1f".format(zoomRatio)}x",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

            }
}

/**
 * 诊断信息叠加层 - 实时显示取景框尺寸、比例和异常信息
 */
@Composable
private fun DiagnosticOverlay(
    bounds: ViewfinderBounds,
    previewAspectRatio: Float,
    screenWidth: Float,
    screenHeight: Float,
    offsetYPx: Float,
    cameraOutputWidth: Int = 0,
    cameraOutputHeight: Int = 0,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    with(density) {
        // 计算关键指标
        val ratioLabel = when (previewAspectRatio) {
            1.0f -> "1:1"
            0.75f -> "4:3"
            0.5625f -> "16:9"
            -1f -> "全屏"
            else -> "自定义($previewAspectRatio)"
        }

        // 相机输出信息
        val cameraInfo = if (cameraOutputWidth > 0 && cameraOutputHeight > 0) {
            val ratio = cameraOutputWidth.toFloat() / cameraOutputHeight
            "相机输出: ${cameraOutputWidth}×${cameraOutputHeight} (${"%.4f".format(ratio)})"
        } else "相机输出: 未知"

        // 计算网格行高（针对16:9）
        val gridRowHeight = if (previewAspectRatio == 0.5625f && bounds.height > 0) {
            val rowHeight = bounds.height / 3
            "网格行高: ${rowHeight.toInt()}px (${rowHeight.toDp().value}dp)"
        } else ""

        // 计算屏幕覆盖率
        val screenCoverage = if (screenWidth > 0 && screenHeight > 0) {
            val coveragePercent = (bounds.width * bounds.height) / (screenWidth * screenHeight) * 100
            "屏幕覆盖: ${"%.1f".format(coveragePercent)}%"
        } else ""

        // 检测异常
        val anomalies = mutableListOf<String>()
        if (bounds.top < 0) anomalies.add("top<0 (${bounds.top.toInt()}px)")
        if (bounds.top + bounds.height > screenHeight) anomalies.add("bottom超出屏幕")
        if (bounds.left < 0) anomalies.add("left<0")
        if (bounds.left + bounds.width > screenWidth) anomalies.add("right超出屏幕")
        if (previewAspectRatio > 0 && bounds.height > 0) {
            val actualRatio = bounds.width / bounds.height
            val expectedRatio = previewAspectRatio
            if (Math.abs(actualRatio - expectedRatio) > 0.02) {
                anomalies.add("比例不匹配: 期望${expectedRatio}, 实际${"%.3f".format(actualRatio)}")
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            // 左上角显示主要信息
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "📐 诊断信息",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "比例: $ratioLabel",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "取景框: ${bounds.width.toInt()}×${bounds.height.toInt()}px",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "位置: (${bounds.left.toInt()}, ${bounds.top.toInt()})",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "偏移: ${offsetYPx.toInt()}px",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = cameraInfo,
                    color = Color.Cyan,
                    fontSize = 14.sp
                )
                if (gridRowHeight.isNotEmpty()) {
                    Text(
                        text = gridRowHeight,
                        color = Color.Yellow,
                        fontSize = 14.sp
                    )
                    // 特别显示三行高度是否相等
                    val rowHeight = bounds.height / 3
                    val row1Top = bounds.top
                    val row2Top = bounds.top + rowHeight
                    val row3Top = bounds.top + rowHeight * 2
                    Text(
                        text = "行1顶: ${row1Top.toInt()}px",
                        color = if (row1Top < 0) Color.Red else Color.Cyan,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "行2顶: ${row2Top.toInt()}px",
                        color = Color.Cyan,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "行3顶: ${row3Top.toInt()}px",
                        color = if (row3Top > screenHeight) Color.Red else Color.Cyan,
                        fontSize = 12.sp
                    )
                }
                if (screenCoverage.isNotEmpty()) {
                    Text(
                        text = screenCoverage,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                if (anomalies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ 异常:",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    anomalies.forEach { anomaly ->
                        Text(
                            text = "  • $anomaly",
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 在取景框周围绘制红色边界线
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(bounds.left, bounds.top),
                    size = androidx.compose.ui.geometry.Size(bounds.width, bounds.height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )

                // 如果是16:9，绘制网格线
                if (previewAspectRatio == 0.5625f && bounds.height > 0) {
                    val rowHeight = bounds.height / 3
                    val lineColor = Color.Yellow.copy(alpha = 0.7f)

                    // 水平网格线
                    for (i in 1..2) {
                        val y = bounds.top + rowHeight * i
                        drawLine(
                            color = lineColor,
                            start = Offset(bounds.left, y),
                            end = Offset(bounds.left + bounds.width, y),
                            strokeWidth = 2f
                        )
                    }

                    // 标记每行高度
                    for (i in 0..2) {
                        val y = bounds.top + rowHeight * i
                        drawCircle(
                            color = Color.Cyan,
                            center = Offset(bounds.left + 10f, y + 10f),
                            radius = 5f
                        )
                    }
                }
            }
        }
    }
}

/**
 * ============================================
 * 后端开发者对接说明
 * ============================================
 *
 * 1. 场景识别对接：
 *    修改 sceneType 变量的赋值逻辑：
 *    ```kotlin
 *    LaunchedEffect(Unit) {
 *        while (true) {
 *            val detectedScene = detectScene() // 你的 AI 模型
 *            sceneType = when (detectedScene) {
 *                SceneType.PORTRAIT -> "人像拍摄"
 *                SceneType.LANDSCAPE -> "风景拍摄"
 *                SceneType.FOOD -> "美食拍摄"
 *                else -> "通用拍摄"
 *            }
 *            delay(500) // 每 500ms 检测一次
 *        }
 *    }
 *    ```
 *
 * 2. 拍照功能对接：
 *    在 onCapture 回调中实现：
 *    ```kotlin
 *    fun capturePhoto(
 *        context: Context,
 *        imageCapture: ImageCapture?,
 *        onSuccess: (String) -> Unit
 *    ) {
 *        val photoFile = File(
 *            context.externalCacheDir,
 *            "photo_${System.currentTimeMillis()}.jpg"
 *        )
 *        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
 *        imageCapture?.takePicture(
 *            outputOptions,
 *            ContextCompat.getMainExecutor(context),
 *            object : ImageCapture.OnImageSavedCallback {
 *                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
 *                    onSuccess(photoFile.absolutePath)
 *                }
 *                override fun onError(exc: ImageCaptureException) {
 *                    Log.e("Camera", "拍照失败", exc)
 *                }
 *            }
 *        )
 *    }
 *    ```
 *
 * 3. 相机参数获取：
 *    ```kotlin
 *    camera.cameraInfo.exposureState.exposureCompensationIndex
 *    camera.cameraInfo.zoomState.value?.linearZoom
 *    // 根据实际 CameraX API 获取参数
 *    ```
 *
 * 4. 闪光灯控制：
 *    ```kotlin
 *    imageCapture?.flashMode = when {
 *        flashEnabled -> ImageCapture.FLASH_MODE_ON
 *        else -> ImageCapture.FLASH_MODE_OFF
 *    }
 *    ```
 *
 * 5. 切换摄像头：
 *    重新绑定相机时更换 cameraSelector:
 *    ```kotlin
 *    val cameraSelector = if (isFrontCamera) {
 *        CameraSelector.DEFAULT_FRONT_CAMERA
 *    } else {
 *        CameraSelector.DEFAULT_BACK_CAMERA
 *    }
 *    ```
 */