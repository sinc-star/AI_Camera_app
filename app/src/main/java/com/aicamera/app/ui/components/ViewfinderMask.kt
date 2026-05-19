package com.aicamera.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

/**
 * 取景框遮罩 - 在预览画面上叠加黑色不透光遮罩，只露出取景框区域
 * 不绘制边框
 */
@Composable
fun ViewfinderMask(bounds: ViewfinderBounds, themeType: ThemeType) {
    val colorScheme = getColorScheme(themeType)

    Canvas(modifier = Modifier.fillMaxSize()) {
        // 预览框外区域绘制60%透明黑边
        val maskAlpha = 1.0f
        val maskColor = Color.Black.copy(alpha = maskAlpha)
        val bottomY = bounds.top + bounds.height

        // 顶部遮罩
        if (bounds.top > 0) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, bounds.top)
            )
        }

        // 底部遮罩
        if (bottomY < size.height) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, bottomY),
                size = Size(size.width, size.height - bottomY)
            )
        }

        // 左右遮罩
        val rightX = bounds.left + bounds.width
        if (bounds.left > 0) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, bounds.top),
                size = Size(bounds.left, bounds.height)
            )
        }
        if (rightX < size.width) {
            drawRect(
                color = maskColor,
                topLeft = Offset(rightX, bounds.top),
                size = Size(size.width - rightX, bounds.height)
            )
        }

        // 已移除边框绘制
    }
}
