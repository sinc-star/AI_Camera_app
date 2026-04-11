package com.aicamera.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import coil.compose.rememberAsyncImagePainter
import com.aicamera.app.ui.components.ToolButton
import com.aicamera.app.ui.components.TopBarWithActions
import com.aicamera.app.ui.theme.*
import com.aicamera.app.ui.theme.getColorScheme
import java.io.File
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.CircularProgressIndicator
import com.aicamera.app.backend.crop.CropBackend
import com.aicamera.app.backend.models.CropMode
import com.aicamera.app.backend.models.CropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import android.graphics.RectF

/**
 * 裁剪界面
 *
 * 功能流程：
 * 1. AI 自动识别并建议裁剪框（2 秒）
 * 2. 用户可手动调整裁剪框
 * 3. 确认后保存裁剪结果
 *
 * 后端开发者注意：
 * ==========================================
 * 【必须接入的 API】
 * ==========================================
 *
 * 1. AI 裁剪识别 API：
 *    POST /api/ai/smart-crop
 *    请求：
 *    {
 *      "imageUri": "file:///path/to/image.jpg",
 *      "cropMode": "auto"  // 可选：portrait, landscape, square
 *    }
 *    响应：
 *    {
 *      "success": true,
 *      "cropRect": {
 *        "left": 0.1,      // 相对位置（0.0-1.0）
 *        "top": 0.15,
 *        "width": 0.8,
 *        "height": 0.7
 *      },
 *      "confidence": 0.92,  // 识别置信度
 *      "suggestion": "检测到人像主体，建议垂直构图",
 *      "detectedSubjects": ["face", "upper_body"]
 *    }
 *
 * 2. 裁剪执行 API：
 *    POST /api/image/crop
 *    请求：
 *    {
 *      "imageUri": "file:///path/to/image.jpg",
 *      "cropRect": {...},  // 最终裁剪框坐标
 *      "outputQuality": 95
 *    }
 *    响应：
 *    {
 *      "success": true,
 *      "outputUri": "file:///path/to/cropped.jpg",
 *      "fileSize": 2048576
 *    }
 */

// 拖拽区域枚举
enum class DragRegion {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    CENTER
}

@Composable
fun CropScreen(
    themeType: ThemeType,
    imageUri: String,
    onNavigateBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colorScheme = getColorScheme(themeType)
    val scope = rememberCoroutineScope()

    // 图片尺寸
    var imageSize by remember { mutableStateOf(Size.Zero) }

    // AI 裁剪状态
    var isAIProcessing by remember { mutableStateOf(true) }
    var aiSuggestion by remember { mutableStateOf("AI 正在分析图片...") }
    var analyzeNonce by remember { mutableStateOf(0) }
    var cropRect by remember {
        mutableStateOf(CropRect(0.1f, 0.2f, 0.8f, 0.6f))
    }

    // 加载图片尺寸
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.Default) {
            try {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(imageUri, options)
                imageSize = Size(options.outWidth.toFloat(), options.outHeight.toFloat())
            } catch (_: Throwable) {
                // 如果无法获取图片尺寸，使用默认值
                imageSize = Size(1000f, 1000f)
            }
        }
    }

    // AI 裁剪识别
    LaunchedEffect(imageUri, analyzeNonce) {
        isAIProcessing = true
        aiSuggestion = "AI 正在分析图片..."
        try {
            val result = withContext(Dispatchers.Default) {
                CropBackend.analyzeSmartCrop(imageUri, CropMode.AUTO)
            }
            cropRect = result.cropRect
            aiSuggestion = result.suggestion
        } catch (_: Throwable) {
            aiSuggestion = "AI 分析失败，请手动调整"
        } finally {
            isAIProcessing = false
        }
    }

    BackHandler {
        onNavigateBack()
    }

    val density = LocalDensity.current
    Box(modifier = Modifier
        .fillMaxSize()
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
        // 图片预览 - 使用 FitCenter 保持比例，与取景框一致
        Image(
            painter = rememberAsyncImagePainter(File(imageUri)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit // 保持比例居中，与取景框一致
        )

        // 裁剪框 - 添加拖拽功能
        CropOverlay(
            cropRect = cropRect,
            isAIProcessing = isAIProcessing,
            imageSize = imageSize,
            onCropRectChanged = { newRect ->
                cropRect = newRect
            },
            themeType = themeType
        )

        // AI 处理提示
        if (isAIProcessing) {
            AIProcessingIndicator(
                modifier = Modifier.align(Alignment.Center),
                themeType = themeType
            )
        }

        // AI 建议提示
        if (!isAIProcessing && aiSuggestion.isNotEmpty()) {
            AISuggestionBanner(
                suggestion = aiSuggestion,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                themeType = themeType
            )
        }

        // 顶部栏
        TopBarWithActions(
            title = "智能裁剪",
            onClose = onNavigateBack,
            onConfirm = {
                isAIProcessing = true
                aiSuggestion = "正在裁剪..."
                scope.launch {
                    try {
                        val output = withContext(Dispatchers.Default) {
                            CropBackend.cropImage(imageUri, cropRect, 95)
                        }
                        onConfirm(output)
                    } catch (_: Throwable) {
                        onConfirm(imageUri)
                    } finally {
                        isAIProcessing = false
                    }
                }
            },
            themeType = themeType,
            modifier = Modifier.padding(top = 24.dp)
        )

        // 底部工具栏
        androidx.compose.material3.Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = if (themeType == ThemeType.FRESH) Color.White else colorScheme.surface.copy(alpha = 0.8f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToolButton(
                    icon = Icons.Default.AutoAwesome,
                    label = "AI 重识别",
                    onClick = {
                        analyzeNonce += 1
                    },
                    themeType = themeType
                )

                ToolButton(
                    icon = Icons.Default.CropSquare,
                    label = "方形",
                    onClick = {
                        val newSize = minOf(cropRect.width, cropRect.height)
                        cropRect = cropRect.copy(width = newSize, height = newSize)
                    },
                    themeType = themeType
                )

                ToolButton(
                    icon = Icons.Default.Crop169,
                    label = "16:9",
                    onClick = {
                        val aspectRatio = 16f / 9f
                        if (cropRect.width / cropRect.height > aspectRatio) {
                            cropRect = cropRect.copy(width = cropRect.height * aspectRatio)
                        } else {
                            cropRect = cropRect.copy(height = cropRect.width / aspectRatio)
                        }
                    },
                    themeType = themeType
                )

                ToolButton(
                    icon = Icons.Default.Refresh,
                    label = "重置",
                    onClick = {
                        cropRect = CropRect(0.1f, 0.2f, 0.8f, 0.6f)
                    },
                    themeType = themeType
                )
            }
        }
    }
}

/**
 * AI 处理指示器
 */
@Composable
private fun AIProcessingIndicator(
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)
    Column(
        modifier = modifier
            .background(
                colorScheme.surface.copy(alpha = 0.8f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AI 正在识别最佳构图...",
            color = colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}

/**
 * AI 建议横幅
 */
@Composable
private fun AISuggestionBanner(
    suggestion: String,
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)
    Row(
        modifier = modifier
            .background(
                colorScheme.primary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = suggestion,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 可拖动的裁剪框叠加层
 */
@Composable
private fun CropOverlay(
    cropRect: CropRect,
    isAIProcessing: Boolean,
    imageSize: Size,
    onCropRectChanged: (CropRect) -> Unit,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)
    var dragRegion by remember { mutableStateOf(DragRegion.NONE) }
    var currentCropRect by remember { mutableStateOf(cropRect) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // 计算图片在画布中的实际显示区域（ContentScale.Fit）
    val imageDisplayRect = remember(canvasSize, imageSize) {
        if (canvasSize.isEmpty() || imageSize.isEmpty()) {
            // 如果尺寸未就绪，返回整个画布
            android.graphics.RectF(0f, 0f, canvasSize.width, canvasSize.height)
        } else {
            // 计算 ContentScale.Fit 的显示区域
            val canvasAspect = canvasSize.width / canvasSize.height
            val imageAspect = imageSize.width / imageSize.height

            val displayWidth: Float
            val displayHeight: Float
            val displayLeft: Float
            val displayTop: Float

            if (imageAspect > canvasAspect) {
                // 图片更宽，以宽度为准
                displayWidth = canvasSize.width
                displayHeight = canvasSize.width / imageAspect
                displayLeft = 0f
                displayTop = (canvasSize.height - displayHeight) / 2
            } else {
                // 图片更高，以高度为准
                displayHeight = canvasSize.height
                displayWidth = canvasSize.height * imageAspect
                displayLeft = (canvasSize.width - displayWidth) / 2
                displayTop = 0f
            }
            android.graphics.RectF(displayLeft, displayTop, displayLeft + displayWidth, displayTop + displayHeight)
        }
    }

    LaunchedEffect(cropRect) {
        currentCropRect = cropRect
    }

    // 当画布大小、图片显示区域或裁剪框变化时，确保裁剪框在图片显示区域内
    LaunchedEffect(canvasSize, imageDisplayRect, currentCropRect) {
        if (canvasSize.isEmpty() || imageDisplayRect.isEmpty()) return@LaunchedEffect

        // 将原始图片比例坐标转换为画布比例坐标
        val canvasLeft = (imageDisplayRect.left + currentCropRect.left * imageDisplayRect.width()) / canvasSize.width
        val canvasTop = (imageDisplayRect.top + currentCropRect.top * imageDisplayRect.height()) / canvasSize.height
        val canvasWidth = currentCropRect.width * imageDisplayRect.width() / canvasSize.width
        val canvasHeight = currentCropRect.height * imageDisplayRect.height() / canvasSize.height

        val imageLeftRatio = imageDisplayRect.left / canvasSize.width
        val imageTopRatio = imageDisplayRect.top / canvasSize.height
        val imageRightRatio = imageDisplayRect.right / canvasSize.width
        val imageBottomRatio = imageDisplayRect.bottom / canvasSize.height

        val clampedCanvasLeft = canvasLeft.coerceIn(imageLeftRatio, imageRightRatio - 0.1f)
        val clampedCanvasTop = canvasTop.coerceIn(imageTopRatio, imageBottomRatio - 0.1f)
        val maxCanvasWidth = imageRightRatio - clampedCanvasLeft
        val maxCanvasHeight = imageBottomRatio - clampedCanvasTop
        val clampedCanvasWidth = canvasWidth.coerceAtMost(maxCanvasWidth).coerceAtLeast(0.1f)
        val clampedCanvasHeight = canvasHeight.coerceAtMost(maxCanvasHeight).coerceAtLeast(0.1f)

        // 如果画布坐标有变化，转换回原始图片比例坐标
        if (canvasLeft != clampedCanvasLeft || canvasTop != clampedCanvasTop ||
            canvasWidth != clampedCanvasWidth || canvasHeight != clampedCanvasHeight) {
            val originalLeft = (clampedCanvasLeft * canvasSize.width - imageDisplayRect.left) / imageDisplayRect.width()
            val originalTop = (clampedCanvasTop * canvasSize.height - imageDisplayRect.top) / imageDisplayRect.height()
            val originalWidth = clampedCanvasWidth * canvasSize.width / imageDisplayRect.width()
            val originalHeight = clampedCanvasHeight * canvasSize.height / imageDisplayRect.height()

            // 限制在0-1范围内
            val clampedOriginalLeft = originalLeft.coerceIn(0f, 1f)
            val clampedOriginalTop = originalTop.coerceIn(0f, 1f)
            val clampedOriginalWidth = originalWidth.coerceIn(0f, 1f - clampedOriginalLeft)
            val clampedOriginalHeight = originalHeight.coerceIn(0f, 1f - clampedOriginalTop)

            val newOriginalCropRect = CropRect(clampedOriginalLeft, clampedOriginalTop, clampedOriginalWidth, clampedOriginalHeight)
            currentCropRect = newOriginalCropRect
            onCropRectChanged(newOriginalCropRect)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(canvasSize, imageDisplayRect) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragRegion = DragRegion.NONE

                    val offset = down.position
                    // 将原始图片比例坐标转换为画布像素坐标
                    val rectLeft = if (imageDisplayRect.isEmpty()) {
                        canvasSize.width * currentCropRect.left
                    } else {
                        imageDisplayRect.left + currentCropRect.left * imageDisplayRect.width()
                    }
                    val rectTop = if (imageDisplayRect.isEmpty()) {
                        canvasSize.height * currentCropRect.top
                    } else {
                        imageDisplayRect.top + currentCropRect.top * imageDisplayRect.height()
                    }
                    val rectRight = if (imageDisplayRect.isEmpty()) {
                        rectLeft + canvasSize.width * currentCropRect.width
                    } else {
                        rectLeft + currentCropRect.width * imageDisplayRect.width()
                    }
                    val rectBottom = if (imageDisplayRect.isEmpty()) {
                        rectTop + canvasSize.height * currentCropRect.height
                    } else {
                        rectTop + currentCropRect.height * imageDisplayRect.height()
                    }

                    dragRegion = detectDragRegion(offset, rectLeft, rectTop, rectRight, rectBottom)

                    if (dragRegion == DragRegion.NONE) return@awaitEachGesture

                    // 将原始图片比例坐标转换为画布比例坐标，用于拖拽计算
                    var canvasCropRect = if (imageDisplayRect.isEmpty()) {
                        currentCropRect
                    } else {
                        val canvasLeft = (imageDisplayRect.left + currentCropRect.left * imageDisplayRect.width()) / canvasSize.width
                        val canvasTop = (imageDisplayRect.top + currentCropRect.top * imageDisplayRect.height()) / canvasSize.height
                        val canvasWidth = currentCropRect.width * imageDisplayRect.width() / canvasSize.width
                        val canvasHeight = currentCropRect.height * imageDisplayRect.height() / canvasSize.height
                        CropRect(canvasLeft, canvasTop, canvasWidth, canvasHeight)
                    }

                    // 计算图片显示区域的相对边界（0-1比例）
                    val imageLeftRatio = imageDisplayRect.left / canvasSize.width
                    val imageTopRatio = imageDisplayRect.top / canvasSize.height
                    val imageRightRatio = imageDisplayRect.right / canvasSize.width
                    val imageBottomRatio = imageDisplayRect.bottom / canvasSize.height

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()

                        if (!change.pressed) {
                            dragRegion = DragRegion.NONE
                            // 将画布比例坐标转换回原始图片比例坐标
                            val newOriginalCropRect = if (imageDisplayRect.isEmpty()) {
                                canvasCropRect
                            } else {
                                val originalLeft = (canvasCropRect.left * canvasSize.width - imageDisplayRect.left) / imageDisplayRect.width()
                                val originalTop = (canvasCropRect.top * canvasSize.height - imageDisplayRect.top) / imageDisplayRect.height()
                                val originalWidth = canvasCropRect.width * canvasSize.width / imageDisplayRect.width()
                                val originalHeight = canvasCropRect.height * canvasSize.height / imageDisplayRect.height()
                                // 限制在0-1范围内
                                val clampedLeft = originalLeft.coerceIn(0f, 1f)
                                val clampedTop = originalTop.coerceIn(0f, 1f)
                                val clampedWidth = originalWidth.coerceIn(0f, 1f - clampedLeft)
                                val clampedHeight = originalHeight.coerceIn(0f, 1f - clampedTop)
                                CropRect(clampedLeft, clampedTop, clampedWidth, clampedHeight)
                            }
                            currentCropRect = newOriginalCropRect
                            onCropRectChanged(newOriginalCropRect)
                            break
                        }

                        val offset = change.position
                        // 将坐标限制在图片显示区域内
                        val clampedX = offset.x.coerceIn(imageDisplayRect.left, imageDisplayRect.right)
                        val clampedY = offset.y.coerceIn(imageDisplayRect.top, imageDisplayRect.bottom)
                        val xRatio = clampedX / canvasSize.width
                        val yRatio = clampedY / canvasSize.height

                        val newRect = when (dragRegion) {
                            DragRegion.TOP_LEFT -> {
                                val newLeft = xRatio.coerceIn(imageLeftRatio, canvasCropRect.left + canvasCropRect.width - 0.1f)
                                val newTop = yRatio.coerceIn(imageTopRatio, canvasCropRect.top + canvasCropRect.height - 0.1f)
                                CropRect(
                                    left = newLeft,
                                    top = newTop,
                                    width = (canvasCropRect.left + canvasCropRect.width - newLeft).coerceAtLeast(0.1f),
                                    height = (canvasCropRect.top + canvasCropRect.height - newTop).coerceAtLeast(0.1f)
                                )
                            }
                            DragRegion.TOP_RIGHT -> {
                                val newTop = yRatio.coerceIn(imageTopRatio, canvasCropRect.top + canvasCropRect.height - 0.1f)
                                val newRight = xRatio.coerceIn(canvasCropRect.left + 0.1f, imageRightRatio)
                                CropRect(
                                    left = canvasCropRect.left,
                                    top = newTop,
                                    width = (newRight - canvasCropRect.left).coerceAtLeast(0.1f),
                                    height = (canvasCropRect.top + canvasCropRect.height - newTop).coerceAtLeast(0.1f)
                                )
                            }
                            DragRegion.BOTTOM_LEFT -> {
                                val newLeft = xRatio.coerceIn(imageLeftRatio, canvasCropRect.left + canvasCropRect.width - 0.1f)
                                val newBottom = yRatio.coerceIn(canvasCropRect.top + 0.1f, imageBottomRatio)
                                CropRect(
                                    left = newLeft,
                                    top = canvasCropRect.top,
                                    width = (canvasCropRect.left + canvasCropRect.width - newLeft).coerceAtLeast(0.1f),
                                    height = (newBottom - canvasCropRect.top).coerceAtLeast(0.1f)
                                )
                            }
                            DragRegion.BOTTOM_RIGHT -> {
                                val newRight = xRatio.coerceIn(canvasCropRect.left + 0.1f, imageRightRatio)
                                val newBottom = yRatio.coerceIn(canvasCropRect.top + 0.1f, imageBottomRatio)
                                CropRect(
                                    left = canvasCropRect.left,
                                    top = canvasCropRect.top,
                                    width = (newRight - canvasCropRect.left).coerceAtLeast(0.1f),
                                    height = (newBottom - canvasCropRect.top).coerceAtLeast(0.1f)
                                )
                            }
                            DragRegion.TOP -> {
                                val newTop = yRatio.coerceIn(imageTopRatio, canvasCropRect.top + canvasCropRect.height - 0.1f)
                                CropRect(
                                    left = canvasCropRect.left,
                                    top = newTop,
                                    width = canvasCropRect.width,
                                    height = (canvasCropRect.top + canvasCropRect.height - newTop).coerceAtLeast(0.1f)
                                )
                            }
                            DragRegion.BOTTOM -> {
                                val newBottom = yRatio.coerceIn(canvasCropRect.top + 0.1f, imageBottomRatio)
                                CropRect(
                                    left = canvasCropRect.left,
                                    top = canvasCropRect.top,
                                    width = canvasCropRect.width,
                                    height = (newBottom - canvasCropRect.top).coerceAtLeast(0.1f)
                                )
                            }
                            DragRegion.LEFT -> {
                                val newLeft = xRatio.coerceIn(imageLeftRatio, canvasCropRect.left + canvasCropRect.width - 0.1f)
                                CropRect(
                                    left = newLeft,
                                    top = canvasCropRect.top,
                                    width = (canvasCropRect.left + canvasCropRect.width - newLeft).coerceAtLeast(0.1f),
                                    height = canvasCropRect.height
                                )
                            }
                            DragRegion.RIGHT -> {
                                val newRight = xRatio.coerceIn(canvasCropRect.left + 0.1f, imageRightRatio)
                                CropRect(
                                    left = canvasCropRect.left,
                                    top = canvasCropRect.top,
                                    width = (newRight - canvasCropRect.left).coerceAtLeast(0.1f),
                                    height = canvasCropRect.height
                                )
                            }
                            DragRegion.CENTER -> {
                                val rectWidth = rectRight - rectLeft
                                val rectHeight = rectBottom - rectTop
                                val halfWidth = rectWidth / 2f
                                val halfHeight = rectHeight / 2f

                                // 限制移动范围在图片显示区域内
                                val minLeft = imageDisplayRect.left
                                val maxLeft = imageDisplayRect.right - rectWidth
                                val minTop = imageDisplayRect.top
                                val maxTop = imageDisplayRect.bottom - rectHeight

                                val newLeftPixel = (offset.x - halfWidth).coerceIn(minLeft, maxLeft)
                                val newTopPixel = (offset.y - halfHeight).coerceIn(minTop, maxTop)

                                CropRect(
                                    left = newLeftPixel / canvasSize.width,
                                    top = newTopPixel / canvasSize.height,
                                    width = canvasCropRect.width,
                                    height = canvasCropRect.height
                                )
                            }
                            DragRegion.NONE -> canvasCropRect
                        }

                        canvasCropRect = newRect
                        change.consume()
                    } while (true)
                }
            }
    ) {
        canvasSize = size
        // 将原始图片比例坐标转换为画布像素坐标
        val cropLeft = if (imageDisplayRect.isEmpty()) {
            size.width * currentCropRect.left
        } else {
            imageDisplayRect.left + currentCropRect.left * imageDisplayRect.width()
        }
        val cropTop = if (imageDisplayRect.isEmpty()) {
            size.height * currentCropRect.top
        } else {
            imageDisplayRect.top + currentCropRect.top * imageDisplayRect.height()
        }
        val cropWidth = if (imageDisplayRect.isEmpty()) {
            size.width * currentCropRect.width
        } else {
            currentCropRect.width * imageDisplayRect.width()
        }
        val cropHeight = if (imageDisplayRect.isEmpty()) {
            size.height * currentCropRect.height
        } else {
            currentCropRect.height * imageDisplayRect.height()
        }

        // 暗色遮罩（裁剪框外）
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, cropTop)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(0f, cropTop + cropHeight),
            size = Size(size.width, size.height - cropTop - cropHeight)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(0f, cropTop),
            size = Size(cropLeft, cropHeight)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(cropLeft + cropWidth, cropTop),
            size = Size(size.width - cropLeft - cropWidth, cropHeight)
        )

        // 裁剪框边框（AI 处理时显示动画效果）
        val borderColor = if (isAIProcessing) {
            Color(0xFFFFD700) // 金色表示 AI 处理中
        } else {
            colorScheme.primary
        }
        val borderWidth = 3f

        // 上边
        drawLine(borderColor, Offset(cropLeft, cropTop), Offset(cropLeft + cropWidth, cropTop), borderWidth)
        // 下边
        drawLine(borderColor, Offset(cropLeft, cropTop + cropHeight), Offset(cropLeft + cropWidth, cropTop + cropHeight), borderWidth)
        // 左边
        drawLine(borderColor, Offset(cropLeft, cropTop), Offset(cropLeft, cropTop + cropHeight), borderWidth)
        // 右边
        drawLine(borderColor, Offset(cropLeft + cropWidth, cropTop), Offset(cropLeft + cropWidth, cropTop + cropHeight), borderWidth)

        // 九宫格辅助线
        val gridColor = borderColor.copy(alpha = 0.5f)
        val gridWidth = 1f

        // 垂直线
        drawLine(gridColor, Offset(cropLeft + cropWidth / 3, cropTop), Offset(cropLeft + cropWidth / 3, cropTop + cropHeight), gridWidth)
        drawLine(gridColor, Offset(cropLeft + cropWidth * 2 / 3, cropTop), Offset(cropLeft + cropWidth * 2 / 3, cropTop + cropHeight), gridWidth)

        // 水平线
        drawLine(gridColor, Offset(cropLeft, cropTop + cropHeight / 3), Offset(cropLeft + cropWidth, cropTop + cropHeight / 3), gridWidth)
        drawLine(gridColor, Offset(cropLeft, cropTop + cropHeight * 2 / 3), Offset(cropLeft + cropWidth, cropTop + cropHeight * 2 / 3), gridWidth)

        // 四个角的拖拽手柄
        val handleSize = 30f
        val handleColor = borderColor

        // 左上角
        drawRect(handleColor, Offset(cropLeft - handleSize/2, cropTop - handleSize/2), Size(handleSize, handleSize))
        // 右上角
        drawRect(handleColor, Offset(cropLeft + cropWidth - handleSize/2, cropTop - handleSize/2), Size(handleSize, handleSize))
        // 左下角
        drawRect(handleColor, Offset(cropLeft - handleSize/2, cropTop + cropHeight - handleSize/2), Size(handleSize, handleSize))
        // 右下角
        drawRect(handleColor, Offset(cropLeft + cropWidth - handleSize/2, cropTop + cropHeight - handleSize/2), Size(handleSize, handleSize))
    }
}

/**
 * 检测拖拽区域
 */
private fun detectDragRegion(offset: Offset, left: Float, top: Float, right: Float, bottom: Float): DragRegion {
    val handleSize = 30f
    val edgeThreshold = 50f

    // 检查是否在角手柄范围内
    if (kotlin.math.abs(offset.x - left) < handleSize && kotlin.math.abs(offset.y - top) < handleSize) {
        return DragRegion.TOP_LEFT
    }
    if (kotlin.math.abs(offset.x - right) < handleSize && kotlin.math.abs(offset.y - top) < handleSize) {
        return DragRegion.TOP_RIGHT
    }
    if (kotlin.math.abs(offset.x - left) < handleSize && kotlin.math.abs(offset.y - bottom) < handleSize) {
        return DragRegion.BOTTOM_LEFT
    }
    if (kotlin.math.abs(offset.x - right) < handleSize && kotlin.math.abs(offset.y - bottom) < handleSize) {
        return DragRegion.BOTTOM_RIGHT
    }

    // 检查是否在边缘范围内
    if (kotlin.math.abs(offset.y - top) < edgeThreshold && offset.x in left..right) {
        return DragRegion.TOP
    }
    if (kotlin.math.abs(offset.y - bottom) < edgeThreshold && offset.x in left..right) {
        return DragRegion.BOTTOM
    }
    if (kotlin.math.abs(offset.x - left) < edgeThreshold && offset.y in top..bottom) {
        return DragRegion.LEFT
    }
    if (kotlin.math.abs(offset.x - right) < edgeThreshold && offset.y in top..bottom) {
        return DragRegion.RIGHT
    }

    // 检查是否在裁剪框内部
    if (offset.x in left..right && offset.y in top..bottom) {
        return DragRegion.CENTER
    }

    return DragRegion.NONE
}
