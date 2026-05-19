package com.aicamera.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.rememberAsyncImagePainter
import com.aicamera.app.ui.theme.*

/**
 * ============================================
 * 可复用组件库
 * ============================================
 * 这个文件包含所有界面共用的组件
 */

/**
 * 1. 圆形图标/文字按钮
 * 用于：相机界面右侧按钮、顶部设置按钮等
 */
@Composable
fun CircleIconButton(
    icon: ImageVector? = null,
    text: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.9f),
    iconTint: Color = ThemeColors.TextPrimary,
    contentDescription: String? = null,
    borderColor: Color? = null,
    useGradient: Boolean = false // 是否使用渐变背景
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = tween(150)
    )

    // 径向渐变：抛光不锈钢金属质感，中心亮周围透明，底色范围更大
    val gradientBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFFFFFAFA),  // 中心微亮
            Color(0xFFF8F8F8),  // 亮银
            Color(0xFFF5F5F5),  // 银白
            Color(0xFFF0F0F0),  // 浅银
            Color(0xFFE8E8E8),  // 浅灰
            Color(0xFFDDDDDD),  // 中浅灰
            Color.White.copy(alpha = 0.3f),  // 淡灰
            Color.White.copy(alpha = 0.1f),  // 接近透明
            Color.Transparent
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (useGradient) {
                    Modifier
                        .background(gradientBrush)
                        .drawBehind {
                            // 偏心高光 - 左上角，柔和过渡
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White,
                                        Color.White.copy(alpha = 0.85f),
                                        Color.White.copy(alpha = 0.6f),
                                        Color.White.copy(alpha = 0.3f),
                                        Color.White.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                ),
                                radius = this.size.minDimension * 0.5f,
                                center = androidx.compose.ui.geometry.Offset(this.size.width * 0.3f, this.size.height * 0.3f)
                            )
                        }
                } else {
                    Modifier.background(backgroundColor)
                }
            )
            .then(
                if (borderColor != null && !useGradient) {
                    Modifier.border(1.dp, borderColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Text(
                text = text,
                fontSize = (iconSize.value / 2).sp,
                color = iconTint,
                fontWeight = FontWeight.Medium
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(iconSize)
                    .scale(scale),
                tint = iconTint
            )
        }
    }
}

/**
 * 2. 主按钮（绿色全圆角按钮）
 * 用于：加载界面的"点击启动"、调色界面的"应用增强"等
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    themeType: ThemeType = ThemeType.PROFESSIONAL
) {
    val colorScheme = getColorScheme(themeType)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary,
            disabledContainerColor = colorScheme.primary.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onPrimary
        )
    }
}

/**
 * 3. 功能卡片
 * 用于：加载界面的功能列表
 */
@Composable
fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    themeType: ThemeType = ThemeType.PROFESSIONAL
) {
    val colorScheme = getColorScheme(themeType)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * 4. 顶部栏（带关闭和确认按钮）
 * 用于：编辑、调色、裁剪界面
 */
@Composable
fun TopBarWithActions(
    title: String,
    onClose: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showConfirm: Boolean = true,
    closeButtonOnRight: Boolean = false,
    titleFontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    themeType: ThemeType = ThemeType.PROFESSIONAL,
    backgroundColor: androidx.compose.ui.graphics.Color? = null,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp
) {
    val colorScheme = getColorScheme(themeType)
    val bgColor = backgroundColor ?: OverlayDark80

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = bgColor,
        shadowElevation = shadowElevation
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 标题（居中）
            Text(
                text = title,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            // 左侧关闭按钮（当closeButtonOnRight为true时隐藏）
            if (!closeButtonOnRight) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = colorScheme.onBackground
                    )
                }
            }

            // 右侧按钮（确认或关闭）
            if (showConfirm && onConfirm != null) {
                IconButton(
                    onClick = onConfirm,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "确认",
                        tint = colorScheme.primary
                    )
                }
            } else if (closeButtonOnRight) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/**
 * 5. 工具按钮（带图标和标签）
 * 用于：编辑界面底部的裁剪、编辑、旋转按钮
 */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    themeType: ThemeType = ThemeType.PROFESSIONAL
) {
    val colorScheme = getColorScheme(themeType)

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图标容器
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) colorScheme.primary.copy(alpha = 0.2f)
                    else if (themeType == ThemeType.FRESH) Color(0xFFEEEEEE)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = if (isSelected) colorScheme.primary
                       else if (themeType == ThemeType.FRESH) Color(0xFF333333)
                       else colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 标签
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) colorScheme.primary
                   else if (themeType == ThemeType.FRESH) Color(0xFF333333)
                   else colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 6. 滑块组件（带标签和数值）
 * 用于：调色界面和设置界面
 */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    valueFormatter: (Float) -> String = { String.format("%.3f", it) },
    steps: Int = 0,
    themeType: ThemeType = ThemeType.PROFESSIONAL
) {
    val colorScheme = getColorScheme(themeType)

    Column(modifier = modifier.fillMaxWidth()) {
        // 标签和数值
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                text = valueFormatter(value),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 滑块 - 紧凑高度
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * 7. AI 增强卡片
 * 用于：调色界面的 AI 增强区域
 */
@Composable
fun AIEnhanceCard(
    detectedInfo: String,
    onApplyEnhance: () -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
    isApplied: Boolean = false,
    themeType: ThemeType = ThemeType.PROFESSIONAL
) {
    val colorScheme = getColorScheme(themeType)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题和图标
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI增强优化",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 检测信息
            Text(
                text = "检测到：$detectedInfo",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 对比按钮
            OutlinedButton(
                onClick = onCompare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "对比",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 应用按钮
            Button(
                onClick = onApplyEnhance,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isApplied,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    disabledContainerColor = colorScheme.primary.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = if (isApplied) "已应用增强" else "应用增强",
                    fontSize = 14.sp,
                    color = if (isApplied) colorScheme.primary else colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * 8. 状态标签（半透明背景）
 * 用于：相机界面顶部的场景识别标签
 */
@Composable
fun StatusLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = OverlayDark
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 15.sp,
                color = TextWhite,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 9. 拍摄按钮（大圆形按钮）
 * 用于：相机界面
 */
@Composable
fun CaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    Box(
        modifier = modifier.size(size + 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 外圈（边框）
        Box(
            modifier = Modifier
                .size(size + 8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
        )

        // 内圈（主按钮）
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onClick)
                .testTag("captureButton")
                .semantics { contentDescription = "拍照" }
        )
    }
}

/**
 * 10. 相机参数显示
 * 用于：相机界面底部的参数显示
 */
@Composable
fun CameraParams(
    iso: String,
    shutter: String,
    aperture: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = OverlayDark
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ISO",
                fontSize = 12.sp,
                color = TextGray
            )
            Text(
                text = iso,
                fontSize = 12.sp,
                color = TextWhite
            )
            Text(
                text = shutter,
                fontSize = 12.sp,
                color = TextWhite
            )
            Text(
                text = aperture,
                fontSize = 12.sp,
                color = TextWhite
            )
        }
    }
}

/**
 * 11. 增强拍摄按钮（支持手势交互）
 * 用于：相机界面主拍摄控制
 * 功能：
 * - 单击：拍照
 * - 长按：开始录像（持续按住）
 * - 长按后上滑：取消操作
 * - 显示录制进度环
 */
@Composable
fun EnhancedCaptureButton(
    onCapture: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    isRecording: Boolean,
    recordingProgress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier.size(size + 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 录制进度环（仅在录制时显示）
        if (isRecording) {
            CircularProgressIndicator(
                progress = { recordingProgress },
                modifier = Modifier.size(size + 12.dp),
                color = Color.Red,
                strokeWidth = 4.dp,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        } else {
            // 外圈（边框）
            Box(
                modifier = Modifier
                    .size(size + 8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }

        // 内圈（主按钮）
        Box(
            modifier = Modifier
                .size(if (isRecording) size * 0.6f else size)
                .scale(scale)
                .clip(CircleShape)
                .background(if (isRecording) Color.Red else Color.White)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                // 检测长按
                                val pressStartTime = System.currentTimeMillis()
                                var isLongPress = false

                                try {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Release) {
                                                break
                                            }
                                            // 超过500ms视为长按
                                            if (!isLongPress && System.currentTimeMillis() - pressStartTime > 500) {
                                                isLongPress = true
                                                onStartRecording()
                                            }
                                        }
                                    }
                                } finally {
                                    isPressed = false
                                    if (isLongPress) {
                                        onStopRecording()
                                    } else {
                                        onCapture()
                                    }
                                }
                            } catch (e: Exception) {
                                isPressed = false
                            }
                        }
                    )
                }
        )
    }
}

/**
 * 12. 相册缩略图按钮
 * 用于：相机界面底部快速访问相册
 */
@Composable
fun GalleryThumbnailButton(
    photoUri: android.net.Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val colorScheme = getColorScheme(ThemeType.PROFESSIONAL)

    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surface,
        onClick = onClick
    ) {
        if (photoUri != null) {
            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(photoUri),
                contentDescription = "最后一张照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = "相册",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 13. 相机信息条（整合状态显示）
 * 用于：相机界面顶部或底部显示拍摄参数
 * 功能：点击展开/收起详细参数
 */
@Composable
fun CameraInfoBar(
    sceneType: String,
    iso: String,
    shutter: String,
    aperture: String,
    modifier: Modifier = Modifier,
    themeType: ThemeType = ThemeType.PROFESSIONAL
) {
    val colorScheme = getColorScheme(themeType)
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent) // 背景由父组件控制（渐变）
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 始终显示的基本信息
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 场景类型（带主题色指示点）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = sceneType,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            // 分隔符
            Text(
                text = "•",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            // ISO
            Text(
                text = "ISO $iso",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            // 展开/收起图标
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }

        // 展开时显示的详细参数
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("快门", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        Text(shutter, fontSize = 12.sp, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("光圈", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        Text(aperture, fontSize = 12.sp, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("白平衡", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        Text("自动", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}