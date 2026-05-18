package com.aicamera.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import com.aicamera.app.ui.components.LabeledSlider
import com.aicamera.app.ui.components.TopBarWithActions
import com.aicamera.app.backend.camera.CameraAdvancedControls
import com.aicamera.app.backend.camera.CameraBackend
import com.aicamera.app.backend.camera.CameraSession
import com.aicamera.app.backend.storage.StorageBackend
import com.aicamera.app.backend.ai.CloudAiService
import com.aicamera.app.backend.ai.AiModelProvider
import com.aicamera.app.ui.theme.*
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    themeType: ThemeType,
    onThemeChange: (ThemeType) -> Unit,
    backgroundAlpha: Float = 1f
) {
    val context = LocalContext.current
    var ev by remember {
        mutableStateOf(
            if (CameraBackend.ManualSettings.evIndex != null) {
                CameraBackend.ManualSettings.evIndex!!.toFloat() / 6f
            } else {
                0f
            }
        )
    }
    var iso by remember {
        mutableStateOf(
            if (CameraBackend.ManualSettings.iso != null) {
                val isoValue = CameraBackend.ManualSettings.iso!!
                ((isoValue - 100).toFloat() / 3100f).coerceIn(0f, 1f)
            } else {
                0.5f
            }
        )
    }
    val camera = CameraSession.camera()
    var evRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(camera) {
        evRange = CameraAdvancedControls.getExposureCompensationRange(camera)
    }

    val minExposureSeconds = 1.0 / 4000.0
    val maxExposureSeconds = 8.0
    val log2Min = kotlin.math.log2(minExposureSeconds)
    val log2Max = kotlin.math.log2(maxExposureSeconds)
    val log2Range = log2Max - log2Min

    var shutter by remember {
        mutableStateOf(
            if (CameraBackend.ManualSettings.exposureTimeNs != null) {
                val exposureNs = CameraBackend.ManualSettings.exposureTimeNs!!
                if (exposureNs == 0L) {
                    0.5f
                } else {
                    val exposureSeconds = exposureNs / 1_000_000_000.0
                    val log2Exposure = kotlin.math.log2(exposureSeconds)
                    ((log2Exposure - log2Min) / log2Range).toFloat().coerceIn(0f, 1f)
                }
            } else {
                0.5f
            }
        )
    }
    var hdr by remember {
        mutableStateOf(
            if (CameraBackend.ManualSettings.hdrEnabled) {
                2
            } else {
                0
            }
        )
    }
    var cacheSizeBytes by remember { mutableStateOf(0L) }
    val aspectRatioOptions = listOf(
        Triple("1:1",  1.0f,   Pair(1f, 1f)),
        Triple("4:3",  0.75f,  Pair(3f, 4f)),
        Triple("16:9", 0.5625f,Pair(9f, 16f)),
        Triple("全屏", -1f,    Pair(9f, 19.5f))
    )
    var selectedAspectRatio by remember {
        mutableStateOf(CameraBackend.ManualSettings.previewAspectRatioPortrait)
    }

    LaunchedEffect(Unit) {
        cacheSizeBytes = StorageBackend.getCacheSize(context)
    }

    BackHandler {
        onNavigateBack()
    }

    val colorScheme = getColorScheme(themeType)
    val isDarkTheme = themeType == ThemeType.PROFESSIONAL ||
                      themeType == ThemeType.TECH

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.30f)
            .offset(y = 0.dp)
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
            .background(
                colorScheme.background.copy(alpha = backgroundAlpha),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBarWithActions(
                title = "设置",
                onClose = onNavigateBack,
                showConfirm = false,
                closeButtonOnRight = true,
                titleFontSize = 18.sp,
                themeType = themeType,
                backgroundColor = if (themeType == ThemeType.FRESH) Color.White else null
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {

                Text(
                    "主题风格",
                    color = colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeOptionCard(
                        title = "专业摄影",
                        subtitle = "琥珀橙配色，专业质感",
                        icon = Icons.Default.CameraAlt,
                        isSelected = themeType == ThemeType.PROFESSIONAL,
                        primaryColor = ProfessionalColors.Primary,
                        backgroundColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
                        contentColor = if (isDarkTheme) Color.White else Color.Black,
                        onClick = { onThemeChange(ThemeType.PROFESSIONAL) }
                    )

                    ThemeOptionCard(
                        title = "科技蓝",
                        subtitle = "霓虹青蓝配色，科技感十足",
                        icon = Icons.Default.Computer,
                        isSelected = themeType == ThemeType.TECH,
                        primaryColor = TechColors.Primary,
                        backgroundColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
                        contentColor = if (isDarkTheme) Color.White else Color.Black,
                        onClick = { onThemeChange(ThemeType.TECH) }
                    )

                    ThemeOptionCard(
                        title = "明亮清新",
                        subtitle = "薄荷绿配色，清新自然",
                        icon = Icons.Default.WbSunny,
                        isSelected = themeType == ThemeType.FRESH,
                        primaryColor = FreshColors.Primary,
                        backgroundColor = if (isDarkTheme) Color.Black else Color.White,
                        contentColor = if (isDarkTheme) Color.White else Color(0xFF1A1A1A),
                        onClick = { onThemeChange(ThemeType.FRESH) }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    "云端AI辅助",
                    color = colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                var cloudAiEnabled by remember { mutableStateOf(CloudAiService.hasApiKey(context)) }
                var apiKeyInput by remember { mutableStateOf("") }
                var apiKeyVisible by remember { mutableStateOf(false) }
                var showApiKeySection by remember { mutableStateOf(false) }
                var selectedProvider by remember { mutableStateOf(CloudAiService.getModelProvider(context)) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "启用云端AI分析",
                        color = colorScheme.onBackground,
                        fontSize = 14.sp
                    )
                    if (themeType == ThemeType.FRESH) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (cloudAiEnabled) Color.Black else Color.White,
                            border = BorderStroke(1.dp, Color.Black),
                            onClick = {
                                if (!cloudAiEnabled && !CloudAiService.hasApiKey(context)) {
                                    showApiKeySection = true
                                } else if (cloudAiEnabled) {
                                    CloudAiService.clearApiKey(context)
                                    cloudAiEnabled = false
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(44.dp)
                                    .padding(2.dp),
                                contentAlignment = if (cloudAiEnabled) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (cloudAiEnabled) Color.White else Color.Black)
                                )
                            }
                        }
                    } else {
                        Switch(
                            checked = cloudAiEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !CloudAiService.hasApiKey(context)) {
                                    showApiKeySection = true
                                } else if (!enabled) {
                                    CloudAiService.clearApiKey(context)
                                    cloudAiEnabled = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorScheme.primary,
                                checkedTrackColor = colorScheme.primary.copy(alpha = 0.6f),
                                uncheckedThumbColor = Color(0xFFE0E0E0),
                                uncheckedTrackColor = Color(0xFF424242),
                                uncheckedBorderColor = Color(0xFF757575)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "选择AI模型",
                    color = colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiModelProvider.entries.forEach { provider ->
                        AiModelOptionCard(
                            provider = provider,
                            isSelected = selectedProvider == provider,
                            primaryColor = colorScheme.primary,
                            backgroundColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
                            contentColor = if (isDarkTheme) Color.White else Color.Black,
                            onClick = {
                                selectedProvider = provider
                                CloudAiService.setModelProvider(context, provider)
                            }
                        )
                    }
                }

                if (showApiKeySection || (cloudAiEnabled && !CloudAiService.hasApiKey(context))) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "API Key (${selectedProvider.displayName})",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = {
                            Text(
                                "输入API Key",
                                color = colorScheme.onSurfaceVariant
                            )
                        },
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (apiKeyVisible) "隐藏" else "显示",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline,
                            focusedTextColor = colorScheme.onBackground,
                            unfocusedTextColor = colorScheme.onBackground,
                            cursorColor = colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    CloudAiService.setApiKey(context, apiKeyInput.trim())
                                    cloudAiEnabled = true
                                    showApiKeySection = false
                                    apiKeyInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            enabled = apiKeyInput.isNotBlank()
                        ) {
                            Text(
                                "保存",
                                color = colorScheme.onPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                showApiKeySection = false
                                apiKeyInput = ""
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.onBackground
                            )
                        ) {
                            Text("取消")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "模型: ${selectedProvider.modelName}",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "获取API Key: ${selectedProvider.helpUrl}",
                        color = colorScheme.secondary,
                        fontSize = 11.sp
                    )
                }

                if (cloudAiEnabled && CloudAiService.hasApiKey(context)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "API Key已配置",
                            color = colorScheme.primary,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = {
                                CloudAiService.clearApiKey(context)
                                cloudAiEnabled = false
                                showApiKeySection = true
                            }
                        ) {
                            Text("修改", color = colorScheme.secondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "缓存大小：${cacheSizeBytes / 1024} KB",
                    color = colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        StorageBackend.clearCache(context)
                        cacheSizeBytes = StorageBackend.getCacheSize(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("清理缓存", color = colorScheme.onPrimary)
                }

            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    primaryColor: Color,
    backgroundColor: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) primaryColor.copy(alpha = 0.15f) else backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) primaryColor else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(primaryColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = primaryColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = if (isSelected) primaryColor else contentColor,
                fontSize = 16.sp
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已选中",
                tint = primaryColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AiModelOptionCard(
    provider: AiModelProvider,
    isSelected: Boolean,
    primaryColor: Color,
    backgroundColor: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) primaryColor.copy(alpha = 0.12f) else backgroundColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) primaryColor else Color.Gray.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = provider.displayName,
                color = if (isSelected) primaryColor else contentColor,
                fontSize = 14.sp
            )
            Text(
                text = provider.description,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已选中",
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
