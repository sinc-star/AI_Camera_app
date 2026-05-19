package com.aicamera.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aicamera.app.backend.gallery.GalleryBackend
import com.aicamera.app.ui.theme.AISmartCameraTheme
import com.aicamera.app.ui.theme.ThemeType
import com.aicamera.app.ui.theme.getColorScheme

/**
 * 底部控制区 - iOS风格布局 [相册][拍摄][反转摄像头]
 */
@Composable
fun BottomControls(
    modifier: Modifier = Modifier,
    iso: String,
    shutter: String,
    aperture: String,
    lastPhotoUri: Uri?,
    onCapture: () -> Unit,
    onFlipCamera: () -> Unit,
    isFullscreen: Boolean = false,
    themeType: ThemeType
) {
    val context = LocalContext.current
    val colorScheme = getColorScheme(themeType)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryThumbnailButton(
                photoUri = lastPhotoUri,
                onClick = {
                    GalleryBackend.openGallery(context, lastPhotoUri)
                },
                size = 72.dp
            )

            CaptureButton(
                onClick = onCapture,
                size = 72.dp
            )

            IconButton(
                onClick = onFlipCamera,
                modifier = Modifier.size(88.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "反转摄像头",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun BottomControlsPreview() {
    AISmartCameraTheme {
        BottomControls(
            iso = "Auto",
            shutter = "Auto",
            aperture = "Auto",
            lastPhotoUri = null,
            onCapture = {},
            onFlipCamera = {},
            themeType = ThemeType.FRESH
        )
    }
}
