package com.aicamera.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.app.ui.theme.AISmartCameraTheme
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

/**
 * 右侧工具栏 — 闪光灯、辅助线、HDR、云端AI、定时器
 */
@Composable
fun SideTools(
    modifier: Modifier = Modifier,
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
    themeType: ThemeType,
    showGradientBg: Boolean = true
) {
    val colorScheme = getColorScheme(themeType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
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

@Preview
@Composable
private fun SideToolsPreview() {
    AISmartCameraTheme {
        SideTools(
            showGuides = true,
            onToggleGuides = {},
            flashEnabled = false,
            onToggleFlash = {},
            hdrEnabled = false,
            onToggleHdr = {},
            timerSeconds = 0,
            onCycleTimer = {},
            cloudAiEnabled = true,
            onToggleCloudAi = {},
            themeType = ThemeType.FRESH
        )
    }
}
