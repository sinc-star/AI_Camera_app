package com.aicamera.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BottomZoomPresets(
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

@Composable
fun ArcZoomSlider(
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
