package com.aicamera.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import coil.compose.rememberAsyncImagePainter
import com.aicamera.app.ui.components.AIEnhanceCard
import com.aicamera.app.ui.components.LabeledSlider
import com.aicamera.app.ui.components.TopBarWithActions
import com.aicamera.app.ui.theme.*
import com.aicamera.app.ui.theme.getColorScheme
import com.aicamera.app.backend.color.ColorBackend
import com.aicamera.app.backend.models.ColorAdjustmentParams
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.animation.Crossfade
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

/**
 * 调色界面
 *
 * 后端开发者注意：
 * - 所有滑块参数范围 -1.0 到 +1.0
 * - 需要实现图片滤镜应用逻辑
 * - AI 增强需要接入算法
 */
@Composable
fun ColorAdjustScreen(
    themeType: ThemeType,
    imageUri: String,
    onNavigateBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colorScheme = getColorScheme(themeType)
    val scope = rememberCoroutineScope()

    // 预览状态
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var updateJob by remember { mutableStateOf<Job?>(null) }

    // 滑块参数 - 使用实际参数值（与模型输出一致）
    var exposure by remember { mutableStateOf(0f) }      // 范围 [-1.0, 1.0]，0 表示不调整
    var contrast by remember { mutableStateOf(1f) }      // 范围 [0.5, 2.0]，1 表示不调整
    var saturation by remember { mutableStateOf(1f) }    // 范围 [0.5, 2.0]，1 表示不调整
    var sharpness by remember { mutableStateOf(0f) }     // 范围 [0, 1]
    var temperature by remember { mutableStateOf(0f) }   // 范围 [-1, 1]
    var highlights by remember { mutableStateOf(0.5f) }  // 范围 [0.0, 1.0]，0.5 表示不调整
    var shadows by remember { mutableStateOf(0.5f) }     // 范围 [0.0, 1.0]，0.5 表示不调整
    var isAIApplied by remember { mutableStateOf(false) }
    var detectedInfo by remember { mutableStateOf("通用场景") }
    var isComparing by remember { mutableStateOf(false) } // 对比模式状态

    // 更新预览函数
    val updatePreview = {
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(200) // 防抖200ms（减少延迟）
            val params = ColorAdjustmentParams(
                exposure = exposure,
                contrast = contrast,
                saturation = saturation,
                sharpness = sharpness,
                temperature = temperature,
                highlights = highlights,
                shadows = shadows
            )
            val bitmap = originalBitmap
            if (bitmap != null) {
                // 使用缓存的原始bitmap生成预览
                previewBitmap = ColorBackend.generatePreviewFromBitmap(bitmap, params)
            } else {
                // 后备方案：从文件加载
                previewBitmap = ColorBackend.generatePreview(imageUri, params)
            }
        }
    }

    // 加载原始图片（仅一次）
    LaunchedEffect(imageUri) {
        if (originalBitmap == null) {
            originalBitmap = withContext(Dispatchers.Default) {
                try {
                    BitmapFactory.decodeFile(imageUri)
                } catch (e: Exception) {
                    null
                }
            }
            // 初始预览
            updatePreview()
        }
    }

    // 组件卸载时取消任务并释放资源
    DisposableEffect(Unit) {
        onDispose {
            updateJob?.cancel()
            // 释放bitmap资源
            originalBitmap?.recycle()
            previewBitmap?.recycle()
            originalBitmap = null
            previewBitmap = null
        }
    }

    BackHandler {
        onNavigateBack()
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()
        .pointerInput(Unit) {
            val threshold = with(density) { 100.dp.toPx() }
            var totalOffset = 0f
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (kotlin.math.abs(totalOffset) > threshold) {
                        onNavigateBack()
                    }
                    totalOffset = 0f
                }
            ) { change, dragAmount ->
                totalOffset += dragAmount
            }
        }
        .background(colorScheme.background)) {
        val sensorRatioPortrait = 0.75f // 3:4 竖屏比例，与相机取景框一致
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // 计算取景框高度（与相机预览保持一致）
        val viewfinderHeight = if (screenWidth.value / screenHeight.value > sensorRatioPortrait) {
            // 屏幕更宽，图像以高度为准缩放
            screenHeight
        } else {
            // 屏幕更高，图像以宽度为准缩放
            screenWidth / sensorRatioPortrait
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            TopBarWithActions(
                title = "",
                onClose = onNavigateBack,
                onConfirm = {
                    val params = ColorAdjustmentParams(
                        exposure = exposure,
                        contrast = contrast,
                        saturation = saturation,
                        sharpness = sharpness,
                        temperature = temperature,
                        highlights = highlights,
                        shadows = shadows
                    )
                    scope.launch {
                        try {
                            val output = withContext(Dispatchers.Default) {
                                ColorBackend.applyColorAdjustments(imageUri, params)
                            }
                            onConfirm(output)
                        } catch (_: Throwable) {
                            onConfirm(imageUri)
                        }
                    }
                },
                themeType = themeType,
                modifier = Modifier.padding(top = 24.dp)
            )

            // 图片预览区域 - 缩小图片，留出更多空间给调整参数
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.35f) // 缩小图片高度，留出更多调整区域
                    .background(Color.Black) // 取景框背景为黑色
            ) {
                // 根据对比模式选择显示的图片
                val currentBitmap = if (isComparing && originalBitmap != null) {
                    originalBitmap
                } else {
                    previewBitmap
                }
                Crossfade(targetState = currentBitmap) { bitmap ->
                    if (bitmap != null) {
                        Image(
                            painter = BitmapPainter(bitmap.asImageBitmap()),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit // 保持比例居中，与取景框一致
                        )
                    } else {
                        Image(
                            painter = rememberAsyncImagePainter(File(imageUri)),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit // 保持比例居中，与取景框一致
                        )
                    }
                }
            }

            // 调色参数区域（60%）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // AI 增强卡片
                AIEnhanceCard(
                    detectedInfo = detectedInfo,
                    onApplyEnhance = {
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.Default) {
                                    ColorBackend.analyzeColorEnhancement(imageUri)
                                }
                                detectedInfo = result.detectedInfo
                                exposure = result.params.exposure
                                contrast = result.params.contrast
                                saturation = result.params.saturation
                                sharpness = result.params.sharpness
                                temperature = result.params.temperature
                                highlights = result.params.highlights
                                shadows = result.params.shadows  // 新增：应用阴影参数
                                isAIApplied = true
                                updatePreview() // 更新预览
                            } catch (_: Throwable) {
                            }
                        }
                    },
                    onCompare = {
                        isComparing = !isComparing // 切换对比模式
                    },
                    isApplied = isAIApplied,
                    themeType = themeType
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 滑块列表 - 直接显示实际参数值
                LabeledSlider(
                    label = "曝光度",
                    value = exposure,
                    onValueChange = { exposure = it; updatePreview() },
                    valueRange = -1.0f..1.0f,
                    valueFormatter = { String.format("%+.3f", it) },
                    themeType = themeType
                )

                Spacer(modifier = Modifier.height(16.dp))

                LabeledSlider(
                    label = "对比度",
                    value = contrast,
                    onValueChange = { contrast = it; updatePreview() },
                    valueRange = 0.5f..2.0f,  // 直接显示实际值
                    valueFormatter = { String.format("%.3f", it) },
                    themeType = themeType
                )

                Spacer(modifier = Modifier.height(16.dp))

                LabeledSlider(
                    label = "饱和度",
                    value = saturation,
                    onValueChange = { saturation = it; updatePreview() },
                    valueRange = 0.5f..2.0f,  // 直接显示实际值
                    valueFormatter = { String.format("%.3f", it) },
                    themeType = themeType
                )

                Spacer(modifier = Modifier.height(16.dp))

                LabeledSlider(
                    label = "锐化",
                    value = sharpness,
                    onValueChange = { sharpness = it; updatePreview() },
                    valueRange = 0f..1f,
                    valueFormatter = { String.format("%.3f", it) },
                    themeType = themeType
                )

                Spacer(modifier = Modifier.height(16.dp))

                LabeledSlider(
                    label = "色温",
                    value = temperature,
                    onValueChange = { temperature = it; updatePreview() },
                    valueRange = -1f..1f,
                    valueFormatter = { if (it > 0) "暖色 +%.3f".format(it) else if (it < 0) "冷色 %.3f".format(-it) else "中性" },
                    themeType = themeType
                )

                Spacer(modifier = Modifier.height(16.dp))

                LabeledSlider(
                    label = "高光",
                    value = highlights,
                    onValueChange = { highlights = it; updatePreview() },
                    valueRange = 0.0f..1.0f,  // 直接显示实际值，0.5 是中性
                    valueFormatter = { String.format("%.3f", it) },
                    themeType = themeType
                )

                Spacer(modifier = Modifier.height(16.dp))

                LabeledSlider(
                    label = "阴影",
                    value = shadows,
                    onValueChange = { shadows = it; updatePreview() },
                    valueRange = 0.0f..1.0f,  // 直接显示实际值，0.5 是中性
                    valueFormatter = { String.format("%.3f", it) },
                    themeType = themeType
                )
            }
        }
    }
}
