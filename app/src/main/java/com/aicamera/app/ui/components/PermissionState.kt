package com.aicamera.app.ui.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

interface PermissionState {
    val isGranted: Boolean
    fun launchMultiplePermissionRequest()
}

@Composable
fun rememberPermissionState(
    permission: String
): PermissionState {
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isGranted = granted
    }
    return object : PermissionState {
        override val isGranted: Boolean = isGranted
        override fun launchMultiplePermissionRequest() {
            launcher.launch(permission)
        }
    }
}
