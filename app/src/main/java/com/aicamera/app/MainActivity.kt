package com.aicamera.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import android.view.WindowManager
import android.os.Build
import android.view.View
import com.aicamera.app.ui.theme.AISmartCameraTheme
import com.aicamera.app.ui.theme.ThemeType
import androidx.compose.runtime.*
import com.aicamera.app.ui.screens.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.aicamera.app.backend.color.ColorBackend
import com.aicamera.app.backend.opencv.OpenCvHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置沉浸式全屏（隐藏状态栏和导航栏）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // 使用View标志实现全屏（兼容更早的Android版本）
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        // 检查并创建桌面快捷方式
        createShortcutIfFirstLaunch()

        setContent {
            // 从SharedPreferences读取主题设置，默认为专业主题
            var themeType by remember {
                mutableStateOf(
                    when (getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .getString("theme_type", "PROFESSIONAL")) {
                        "TECH" -> ThemeType.TECH
                        "FRESH" -> ThemeType.FRESH
                        else -> ThemeType.PROFESSIONAL
                    }
                )
            }

            AISmartCameraTheme(themeType = themeType) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ){
                        // 传递主题设置函数给AppNavigation
                        AppNavigationWithTheme(
                            themeType = themeType,
                            onThemeChange = { newTheme ->
                                themeType = newTheme
                                // 保存主题设置到SharedPreferences
                                getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("theme_type", newTheme.name)
                                    .apply()
                            }
                        )
                    }
                }
            }
        }

        // 延迟初始化非关键资源，减少启动时间
        lifecycleScope.launch {
            // 初始化 ONNX 模型
            val success = ColorBackend.initialize(this@MainActivity)
            android.util.Log.i("MainActivity", "ONNX model initialization: ${if (success) "success" else "failed"}")
            
            // 初始化 OpenCV（用于构图分析）
            val cvSuccess = OpenCvHelper.init()
            android.util.Log.i("MainActivity", "OpenCV initialization: ${if (cvSuccess) "success" else "failed"}")
        }
    }
    
    /**
     * 首次启动时创建桌面快捷方式
     */
    private fun createShortcutIfFirstLaunch() {
        val prefs: SharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        
        if (isFirstLaunch) {
            createShortcut()
            prefs.edit().putBoolean("is_first_launch", false).apply()
        }
    }
    
    /**
     * 创建桌面快捷方式
     */
    private fun createShortcut() {
        try {
            val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            // 使用广播方式创建快捷方式（兼容性更好）
            val installIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT")
            installIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "AI Smart Camera")
            installIntent.putExtra("duplicate", false)
            installIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher))
            installIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            sendBroadcast(installIntent)
        } catch (e: Exception) {
            // 创建失败不影响应用启动
        }
    }
}

/**
 * 应用导航状态
 */
sealed class Screen {
    object Splash : Screen()              // 加载界面
    object Camera : Screen()               // 主相机界面
    data class Edit(val imageUri: String) : Screen()      // 编辑界面
    data class Color(val imageUri: String) : Screen()     // 调色界面
    data class Crop(val imageUri: String) : Screen()      // 裁剪界面
    object Settings : Screen()             // 设置界面
}


/**
 * 应用主导航（带主题支持）
 *
 * 这个函数管理整个应用的屏幕切换
 * 后端开发者：只需要调用 currentScreen = Screen.XXX 来切换屏幕
 */
@Composable
fun AppNavigationWithTheme(
    themeType: ThemeType,
    onThemeChange: (ThemeType) -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }

    when (val screen = currentScreen) {
        is Screen.Splash -> {
            SplashScreen(
                themeType = themeType,
                onNavigateToCamera = {
                    currentScreen = Screen.Camera
                }
            )
        }

        is Screen.Camera -> {
            CameraScreen(
                themeType = themeType,
                onNavigateToEdit = { imageUri ->
                    currentScreen = Screen.Edit(imageUri)
                },
                onNavigateToSettings = {
                    currentScreen = Screen.Settings
                }
            )
        }

        is Screen.Edit -> {
            EditScreen(
                themeType = themeType,
                imageUri = screen.imageUri,
                onNavigateBack = {
                    currentScreen = Screen.Camera
                },
                onNavigateToCrop = {
                    currentScreen = Screen.Crop(screen.imageUri)
                },
                onNavigateToColor = {
                    currentScreen = Screen.Color(screen.imageUri)
                },
                onImageRotated = { rotatedUri ->
                    currentScreen = Screen.Edit(rotatedUri)
                }
            )
        }

        is Screen.Color -> {
            ColorAdjustScreen(
                themeType = themeType,
                imageUri = screen.imageUri,
                onNavigateBack = {
                    currentScreen = Screen.Edit(screen.imageUri)
                },
                onConfirm = { outputUri ->
                    // 应用颜色调整后返回编辑界面（使用新图片）
                    currentScreen = Screen.Edit(outputUri)
                }
            )
        }

        is Screen.Crop -> {
            CropScreen(
                themeType = themeType,
                imageUri = screen.imageUri,
                onNavigateBack = {
                    currentScreen = Screen.Edit(screen.imageUri)
                },
                onConfirm = { outputUri ->
                    // 裁剪完成后返回编辑界面（使用新图片）
                    currentScreen = Screen.Edit(outputUri)
                }
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                onNavigateBack = {
                    currentScreen = Screen.Camera
                },
                themeType = themeType,
                onThemeChange = onThemeChange
            )
        }
    }
}





@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AISmartCameraTheme {
        AppNavigationWithTheme(
            themeType = ThemeType.PROFESSIONAL,
            onThemeChange = {}
        )
    }
}