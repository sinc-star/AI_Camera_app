package com.aicamera.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 诊断信息叠加层 - 实时显示取景框尺寸、比例和异常信息
 */
@Composable
fun DiagnosticOverlay(
    bounds: ViewfinderBounds,
    previewAspectRatio: Float,
    screenWidth: Float,
    screenHeight: Float,
    offsetYPx: Float,
    cameraOutputWidth: Int = 0,
    cameraOutputHeight: Int = 0,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    with(density) {
        // 计算关键指标
        val ratioLabel = when (previewAspectRatio) {
            1.0f -> "1:1"
            0.75f -> "4:3"
            0.5625f -> "16:9"
            -1f -> "全屏"
            else -> "自定义($previewAspectRatio)"
        }

        // 相机输出信息
        val cameraInfo = if (cameraOutputWidth > 0 && cameraOutputHeight > 0) {
            val ratio = cameraOutputWidth.toFloat() / cameraOutputHeight
            "相机输出: ${cameraOutputWidth}×${cameraOutputHeight} (${"%.4f".format(ratio)})"
        } else "相机输出: 未知"

        // 计算网格行高（针对16:9）
        val gridRowHeight = if (previewAspectRatio == 0.5625f && bounds.height > 0) {
            val rowHeight = bounds.height / 3
            "网格行高: ${rowHeight.toInt()}px (${rowHeight.toDp().value}dp)"
        } else ""

        // 计算屏幕覆盖率
        val screenCoverage = if (screenWidth > 0 && screenHeight > 0) {
            val coveragePercent = (bounds.width * bounds.height) / (screenWidth * screenHeight) * 100
            "屏幕覆盖: ${"%.1f".format(coveragePercent)}%"
        } else ""

        // 检测异常
        val anomalies = mutableListOf<String>()
        if (bounds.top < 0) anomalies.add("top<0 (${bounds.top.toInt()}px)")
        if (bounds.top + bounds.height > screenHeight) anomalies.add("bottom超出屏幕")
        if (bounds.left < 0) anomalies.add("left<0")
        if (bounds.left + bounds.width > screenWidth) anomalies.add("right超出屏幕")
        if (previewAspectRatio > 0 && bounds.height > 0) {
            val actualRatio = bounds.width / bounds.height
            val expectedRatio = previewAspectRatio
            if (Math.abs(actualRatio - expectedRatio) > 0.02) {
                anomalies.add("比例不匹配: 期望${expectedRatio}, 实际${"%.3f".format(actualRatio)}")
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            // 左上角显示主要信息
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "📐 诊断信息",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "比例: $ratioLabel",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "取景框: ${bounds.width.toInt()}×${bounds.height.toInt()}px",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "位置: (${bounds.left.toInt()}, ${bounds.top.toInt()})",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "偏移: ${offsetYPx.toInt()}px",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = cameraInfo,
                    color = Color.Cyan,
                    fontSize = 14.sp
                )
                if (gridRowHeight.isNotEmpty()) {
                    Text(
                        text = gridRowHeight,
                        color = Color.Yellow,
                        fontSize = 14.sp
                    )
                    // 特别显示三行高度是否相等
                    val rowHeight = bounds.height / 3
                    val row1Top = bounds.top
                    val row2Top = bounds.top + rowHeight
                    val row3Top = bounds.top + rowHeight * 2
                    Text(
                        text = "行1顶: ${row1Top.toInt()}px",
                        color = if (row1Top < 0) Color.Red else Color.Cyan,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "行2顶: ${row2Top.toInt()}px",
                        color = Color.Cyan,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "行3顶: ${row3Top.toInt()}px",
                        color = if (row3Top > screenHeight) Color.Red else Color.Cyan,
                        fontSize = 12.sp
                    )
                }
                if (screenCoverage.isNotEmpty()) {
                    Text(
                        text = screenCoverage,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                if (anomalies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ 异常:",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    anomalies.forEach { anomaly ->
                        Text(
                            text = "  • $anomaly",
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 在取景框周围绘制红色边界线
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height),
                    style = Stroke(width = 3f)
                )

                // 如果是16:9，绘制网格线
                if (previewAspectRatio == 0.5625f && bounds.height > 0) {
                    val rowHeight = bounds.height / 3
                    val lineColor = Color.Yellow.copy(alpha = 0.7f)

                    // 水平网格线
                    for (i in 1..2) {
                        val y = bounds.top + rowHeight * i
                        drawLine(
                            color = lineColor,
                            start = Offset(bounds.left, y),
                            end = Offset(bounds.left + bounds.width, y),
                            strokeWidth = 2f
                        )
                    }

                    // 标记每行高度
                    for (i in 0..2) {
                        val y = bounds.top + rowHeight * i
                        drawCircle(
                            color = Color.Cyan,
                            center = Offset(bounds.left + 10f, y + 10f),
                            radius = 5f
                        )
                    }
                }
            }
        }
    }
}
