package com.aicamera.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

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
fun CompositionGuides(bounds: ViewfinderBounds, themeType: ThemeType) {
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
