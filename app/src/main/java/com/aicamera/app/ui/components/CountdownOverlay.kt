package com.aicamera.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

/**
 * 倒计时覆盖层
 */
@Composable
fun CountdownOverlay(
    seconds: Int,
    modifier: Modifier = Modifier,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(colorScheme.surface.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = seconds.toString(),
            fontSize = 48.sp,
            color = colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}
