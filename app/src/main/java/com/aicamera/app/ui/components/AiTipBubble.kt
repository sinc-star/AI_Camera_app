package com.aicamera.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

/**
 * 统一的AI建议提示气泡
 *
 * 用于显示云端或本地AI的构图建议
 * 云端建议：紫色主题 + 云图标
 * 本地建议：绿色主题 + 灯泡图标
 */
@Composable
fun AiTipBubble(
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
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
