package com.aicamera.app.ui.panels

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.app.backend.camera.CameraBackend
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

/**
 * ============================================
 * 画幅比例面板 - iOS风格底部呼出
 * ============================================
 */
@Composable
fun AspectRatioPanel(
    onDismiss: () -> Unit,
    themeType: ThemeType = ThemeType.PROFESSIONAL
) {
    val colorScheme = getColorScheme(themeType)
    var selectedRatio by remember {
        mutableStateOf(CameraBackend.ManualSettings.previewAspectRatioPortrait)
    }

    // 使用 derivedStateOf 监听比例变化，替代轮询
    val currentBackendRatio by remember {
        derivedStateOf { CameraBackend.ManualSettings.previewAspectRatioPortrait }
    }

    // 当后端比例变化时更新本地状态
    LaunchedEffect(currentBackendRatio) {
        if (currentBackendRatio != selectedRatio) {
            selectedRatio = currentBackendRatio
        }
    }

    val ratioOptions = listOf(
        AspectRatioOption("1:1", "正方形", 1.0f, 1f to 1f),
        AspectRatioOption("4:3", "标准", 0.75f, 3f to 4f),
        AspectRatioOption("16:9", "宽屏", 0.5625f, 9f to 16f),
        AspectRatioOption("全屏", "填充屏幕", -1f, 9f to 19.5f)
    )

    // iOS风格底部面板
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount > 50) onDismiss()
                    }
                }
                .clickable { /* 阻止穿透 */ },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // 顶部拖动指示条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "画幅比例",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))

                // 2x2网格选项
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ratioOptions.chunked(2).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowOptions.forEach { option ->
                                AspectRatioButton(
                                    option = option,
                                    isSelected = selectedRatio == option.ratioValue,
                                    onSelect = {
                                        selectedRatio = option.ratioValue
                                        // 直接更新后端设置，避免协程问题
                                        CameraBackend.ManualSettings.previewAspectRatioPortrait = option.ratioValue
                                    },
                                    modifier = Modifier.weight(1f),
                                    themeType = themeType
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 当前比例显示
                val currentLabel = ratioOptions.find { it.ratioValue == selectedRatio }?.label ?: "4:3"
                Text(
                    text = "当前比例: $currentLabel",
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

private data class AspectRatioOption(
    val label: String,
    val description: String,
    val ratioValue: Float,
    val displayRatio: Pair<Float, Float>
)

@Composable
private fun AspectRatioButton(
    option: AspectRatioOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)
    val borderColor = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val gradientColors = listOf(
        colorScheme.primary,
        colorScheme.primary.copy(alpha = 0.6f)
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.background(brush = Brush.verticalGradient(gradientColors))
                } else {
                    Modifier
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                }
            )
            .clickable(onClick = onSelect)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val (bw, bh) = option.displayRatio
        val maxSize = 48f
        val scale = kotlin.math.min(maxSize / bw, maxSize / bh)
        val boxW = (bw * scale).dp
        val boxH = (bh * scale).dp

        Box(
            modifier = Modifier
                .size(width = boxW, height = boxH)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = 2.dp,
                    color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                )
                .background(if (isSelected) colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = option.label,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else colorScheme.onSurface
        )

        Text(
            text = option.description,
            fontSize = 12.sp,
            color = if (isSelected) Color.White.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant
        )

        // 添加选择动画
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}