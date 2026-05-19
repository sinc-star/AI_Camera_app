package com.aicamera.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aicamera.app.ui.theme.AISmartCameraTheme
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

/**
 * 顶部栏组件 — 直接显示画幅比例按钮
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    sceneType: String,
    iso: String,
    shutter: String,
    previewAspectRatio: Float,
    onAspectRatioChanged: (Float) -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowParamSettings: () -> Unit,
    themeType: ThemeType
) {
    val colorScheme = getColorScheme(themeType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(40.dp))

        CircleIconButton(
            icon = Icons.Default.Settings,
            onClick = onNavigateToSettings,
            backgroundColor = Color.Transparent,
            iconTint = if (themeType == ThemeType.FRESH) Color.White else colorScheme.onBackground,
            contentDescription = "设置",
            size = 40.dp,
            iconSize = 22.dp
        )
    }
}

@Preview
@Composable
private fun TopBarPreview() {
    AISmartCameraTheme {
        TopBar(
            sceneType = "通用拍摄",
            iso = "Auto",
            shutter = "Auto",
            previewAspectRatio = 0.75f,
            onAspectRatioChanged = {},
            onNavigateToSettings = {},
            onShowParamSettings = {},
            themeType = ThemeType.FRESH
        )
    }
}
