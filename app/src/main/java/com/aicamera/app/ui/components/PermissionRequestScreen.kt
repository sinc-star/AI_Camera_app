package com.aicamera.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

/**
 * 权限请求界面
 */
@Composable
fun PermissionRequestScreen(
    themeType: ThemeType,
    onRequestPermission: () -> Unit
) {
    val colorScheme = getColorScheme(themeType)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            CircleIconButton(
                icon = Icons.Default.CameraAlt,
                onClick = {},
                size = 80.dp,
                iconSize = 40.dp,
                backgroundColor = colorScheme.surface
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = "授予相机权限",
                onClick = onRequestPermission,
                icon = Icons.Default.Camera,
                themeType = themeType
            )
        }
    }
}
