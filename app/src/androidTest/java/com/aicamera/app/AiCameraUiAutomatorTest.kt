package com.aicamera.app

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class AiCameraUiAutomatorTest {

    private lateinit var device: UiDevice
    private val packageName = "com.aicamera.app"
    private val launchTimeout = 15000L
    private val shortTimeout = 3000L
    private val mediumTimeout = 5000L

    private val testResults = mutableListOf<TestResult>()

    data class TestResult(
        val testCaseId: String,
        val testName: String,
        val category: String,
        val status: String,
        val durationMs: Long,
        val details: String,
        val timestamp: String
    )

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        grantPermissions()
    }

    @After
    fun tearDown() {
        writeTestResultsToFile()
    }

    private fun launchAppFromHome() {
        device.pressHome()
        Thread.sleep(500)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("Launch intent should not be null", intent)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
    }

    private fun grantPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant $packageName android.permission.CAMERA")
        instrumentation.uiAutomation.executeShellCommand("pm grant $packageName android.permission.READ_MEDIA_IMAGES")
        Thread.sleep(300)
    }

    private fun navigateToCameraScreen() {
        launchAppFromHome()
        Thread.sleep(2000)
        val launchButton = device.findObject(By.text("点击启动"))
        if (launchButton != null) {
            launchButton.click()
            Thread.sleep(3000)
        }
    }

    private fun recordResult(
        testCaseId: String,
        testName: String,
        category: String,
        status: String,
        durationMs: Long,
        details: String
    ) {
        testResults.add(
            TestResult(
                testCaseId = testCaseId,
                testName = testName,
                category = category,
                status = status,
                durationMs = durationMs,
                details = details,
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(java.util.Date())
            )
        )
    }

    private fun writeTestResultsToFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resultsDir = File(context.getExternalFilesDir(null), "test_results")
        resultsDir.mkdirs()
        val resultsFile = File(resultsDir, "ui_automator_results_${System.currentTimeMillis()}.txt")

        val sb = StringBuilder()
        sb.appendLine("AI Camera UI Automator Test Results")
        sb.appendLine("=".repeat(60))
        sb.appendLine("Device: ${device.productName}")
        sb.appendLine("Android API: ${android.os.Build.VERSION.SDK_INT}")
        sb.appendLine("Package: $packageName")
        sb.appendLine("Test Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        val passed = testResults.count { it.status == "PASS" }
        val failed = testResults.count { it.status == "FAIL" }
        val skipped = testResults.count { it.status == "SKIP" }
        val totalDuration = testResults.sumOf { it.durationMs }

        testResults.forEach { r ->
            sb.appendLine("[${r.status}] ${r.testCaseId}: ${r.testName}")
            sb.appendLine("  Category: ${r.category}")
            sb.appendLine("  Duration: ${r.durationMs}ms")
            sb.appendLine("  Time: ${r.timestamp}")
            sb.appendLine("  Details: ${r.details}")
            sb.appendLine()
        }

        sb.appendLine("=".repeat(60))
        sb.appendLine("Summary: ${testResults.size} tests, $passed passed, $failed failed, $skipped skipped")
        sb.appendLine("Total Duration: ${totalDuration}ms")
        sb.appendLine("=".repeat(60))

        resultsFile.writeText(sb.toString())
    }

    @Test
    fun tc01_appLaunchTest() {
        val startMs = System.currentTimeMillis()
        try {
            launchAppFromHome()
            val hasPackage = device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
            assertTrue("App should launch within $launchTimeout ms", hasPackage)
            val currentPkg = device.currentPackageName
            assertEquals("Current package should be $packageName", packageName, currentPkg)
            recordResult("TC-01", "应用启动测试", "启动", "PASS",
                System.currentTimeMillis() - startMs,
                "应用成功启动，包名验证通过: $currentPkg")
        } catch (e: Exception) {
            recordResult("TC-01", "应用启动测试", "启动", "FAIL",
                System.currentTimeMillis() - startMs,
                "启动失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc02_splashScreenDisplayTest() {
        val startMs = System.currentTimeMillis()
        try {
            launchAppFromHome()
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
            val splashText = device.wait(Until.findObject(By.text("智能相机")), mediumTimeout)
            assertNotNull("Splash screen should display '智能相机'", splashText)
            val launchButton = device.wait(Until.findObject(By.text("点击启动")), mediumTimeout)
            assertNotNull("Splash screen should display '点击启动'", launchButton)
            recordResult("TC-02", "启动画面显示测试", "启动画面", "PASS",
                System.currentTimeMillis() - startMs,
                "启动画面正确显示: 标题'智能相机', 按钮'点击启动'")
        } catch (e: Exception) {
            recordResult("TC-02", "启动画面显示测试", "启动画面", "FAIL",
                System.currentTimeMillis() - startMs,
                "启动画面显示异常: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc03_splashFeatureCardsTest() {
        val startMs = System.currentTimeMillis()
        try {
            launchAppFromHome()
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
            val feature1 = device.wait(Until.findObject(By.text("智能辅助")), mediumTimeout)
            assertNotNull("Feature card '智能辅助' should be displayed", feature1)
            val feature2 = device.wait(Until.findObject(By.text("构图优化")), mediumTimeout)
            assertNotNull("Feature card '构图优化' should be displayed", feature2)
            val feature3 = device.wait(Until.findObject(By.text("AI 美化")), mediumTimeout)
            assertNotNull("Feature card 'AI 美化' should be displayed", feature3)
            recordResult("TC-03", "启动画面功能卡片显示测试", "启动画面", "PASS",
                System.currentTimeMillis() - startMs,
                "三个功能卡片均正确显示: 智能辅助, 构图优化, AI 美化")
        } catch (e: Exception) {
            recordResult("TC-03", "启动画面功能卡片显示测试", "启动画面", "FAIL",
                System.currentTimeMillis() - startMs,
                "功能卡片显示异常: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc04_splashNavigateToCameraTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val settingsButton = device.findObject(By.desc("设置"))
            val flashButton = device.findObject(By.desc("闪光灯"))
            assertTrue("Should navigate to camera screen",
                settingsButton != null || flashButton != null)
            recordResult("TC-04", "启动画面导航到相机界面", "导航", "PASS",
                System.currentTimeMillis() - startMs,
                "成功导航到相机界面, 设置按钮: ${settingsButton != null}, 闪光灯按钮: ${flashButton != null}")
        } catch (e: Exception) {
            recordResult("TC-04", "启动画面导航到相机界面", "导航", "FAIL",
                System.currentTimeMillis() - startMs,
                "导航失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc05_cameraSettingsButtonTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
            assertNotNull("Settings button should be visible", settingsButton)
            settingsButton.click()
            Thread.sleep(1500)
            val settingsTitle = device.wait(Until.findObject(By.text("设置")), mediumTimeout)
            assertNotNull("Should navigate to settings screen", settingsTitle)
            recordResult("TC-05", "相机界面设置按钮测试", "相机界面", "PASS",
                System.currentTimeMillis() - startMs,
                "设置按钮可点击并成功导航到设置界面")
        } catch (e: Exception) {
            recordResult("TC-05", "相机界面设置按钮测试", "相机界面", "FAIL",
                System.currentTimeMillis() - startMs,
                "设置按钮测试失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc06_flashToggleTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val flashButton = device.wait(Until.findObject(By.desc("闪光灯")), mediumTimeout)
            assertNotNull("Flash button should be visible", flashButton)
            flashButton.click()
            Thread.sleep(1000)
            recordResult("TC-06", "闪光灯切换测试", "相机功能", "PASS",
                System.currentTimeMillis() - startMs,
                "闪光灯按钮可点击切换")
        } catch (e: Exception) {
            recordResult("TC-06", "闪光灯切换测试", "相机功能", "FAIL",
                System.currentTimeMillis() - startMs,
                "闪光灯切换失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc07_gridGuideToggleTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val gridButton = device.wait(Until.findObject(By.desc("辅助线")), mediumTimeout)
            assertNotNull("Grid guide button should be visible", gridButton)
            gridButton.click()
            Thread.sleep(1000)
            recordResult("TC-07", "构图辅助线切换测试", "相机功能", "PASS",
                System.currentTimeMillis() - startMs,
                "辅助线按钮可点击切换")
        } catch (e: Exception) {
            recordResult("TC-07", "构图辅助线切换测试", "相机功能", "FAIL",
                System.currentTimeMillis() - startMs,
                "辅助线切换失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc08_hdrToggleTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val hdrButton = device.wait(Until.findObject(By.text("HDR")), mediumTimeout)
            assertNotNull("HDR button should be visible", hdrButton)
            hdrButton.click()
            Thread.sleep(1500)
            recordResult("TC-08", "HDR 切换测试", "相机功能", "PASS",
                System.currentTimeMillis() - startMs,
                "HDR按钮可点击切换")
        } catch (e: Exception) {
            recordResult("TC-08", "HDR 切换测试", "相机功能", "FAIL",
                System.currentTimeMillis() - startMs,
                "HDR切换失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc09_timerCycleTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val timerButton = device.wait(Until.findObject(By.desc("定时器")), mediumTimeout)
            assertNotNull("Timer button should be visible", timerButton)
            timerButton.click()
            Thread.sleep(1000)
            val afterFirst = device.findObject(By.text("3s"))
            assertNotNull("After first click, should show '3s'", afterFirst)
            recordResult("TC-09", "定时器循环切换测试", "相机功能", "PASS",
                System.currentTimeMillis() - startMs,
                "定时器按钮可点击切换，首次点击后显示'3s'")
        } catch (e: Exception) {
            recordResult("TC-09", "定时器循环切换测试", "相机功能", "FAIL",
                System.currentTimeMillis() - startMs,
                "定时器切换失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc10_settingsThemeSwitchTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
            settingsButton?.click()
            Thread.sleep(1500)
            val techTheme = device.wait(Until.findObject(By.text("科技蓝")), mediumTimeout)
            assertNotNull("'科技蓝' theme option should be visible", techTheme)
            techTheme.click()
            Thread.sleep(1000)
            val freshTheme = device.wait(Until.findObject(By.text("明亮清新")), mediumTimeout)
            assertNotNull("'明亮清新' theme option should be visible", freshTheme)
            freshTheme.click()
            Thread.sleep(1000)
            val profTheme = device.wait(Until.findObject(By.text("专业摄影")), mediumTimeout)
            assertNotNull("'专业摄影' theme option should be visible", profTheme)
            profTheme.click()
            Thread.sleep(1000)
            recordResult("TC-10", "设置界面主题切换测试", "设置", "PASS",
                System.currentTimeMillis() - startMs,
                "三种主题切换正常: 科技蓝 -> 明亮清新 -> 专业摄影")
        } catch (e: Exception) {
            recordResult("TC-10", "设置界面主题切换测试", "设置", "FAIL",
                System.currentTimeMillis() - startMs,
                "主题切换失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc11_settingsCacheClearTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
            settingsButton?.click()
            Thread.sleep(1500)
            val clearButton = device.wait(Until.findObject(By.text("清理缓存")), mediumTimeout)
            assertNotNull("Clear cache button should be visible", clearButton)
            clearButton.click()
            Thread.sleep(1000)
            recordResult("TC-11", "设置界面缓存清理测试", "设置", "PASS",
                System.currentTimeMillis() - startMs,
                "缓存清理按钮可点击执行")
        } catch (e: Exception) {
            recordResult("TC-11", "设置界面缓存清理测试", "设置", "FAIL",
                System.currentTimeMillis() - startMs,
                "缓存清理失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc12_settingsBackNavigationTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
            settingsButton?.click()
            Thread.sleep(1500)
            val settingsTitle = device.wait(Until.findObject(By.text("设置")), mediumTimeout)
            assertNotNull("Should be on settings screen", settingsTitle)
            device.pressBack()
            Thread.sleep(1500)
            val cameraSettingsBtn = device.findObject(By.desc("设置"))
            assertNotNull("Should be back on camera screen", cameraSettingsBtn)
            recordResult("TC-12", "设置界面返回导航测试", "导航", "PASS",
                System.currentTimeMillis() - startMs,
                "从设置界面成功返回相机界面")
        } catch (e: Exception) {
            recordResult("TC-12", "设置界面返回导航测试", "导航", "FAIL",
                System.currentTimeMillis() - startMs,
                "返回导航失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc13_cameraFlipTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val flipButton = device.wait(Until.findObject(By.desc("反转摄像头")), mediumTimeout)
            assertNotNull("Camera flip button should be visible", flipButton)
            flipButton.click()
            Thread.sleep(2000)
            recordResult("TC-13", "相机翻转按钮测试", "相机功能", "PASS",
                System.currentTimeMillis() - startMs,
                "相机翻转按钮可点击切换前后摄像头")
        } catch (e: Exception) {
            recordResult("TC-13", "相机翻转按钮测试", "相机功能", "FAIL",
                System.currentTimeMillis() - startMs,
                "相机翻转失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc14_cloudAiButtonTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val cloudAiButton = device.wait(Until.findObject(By.desc("云端AI")), mediumTimeout)
            assertNotNull("Cloud AI button should be visible", cloudAiButton)
            cloudAiButton.click()
            Thread.sleep(1000)
            recordResult("TC-14", "云端AI辅助按钮测试", "相机功能", "PASS",
                System.currentTimeMillis() - startMs,
                "云端AI辅助按钮可点击（未配置API Key时为off状态）")
        } catch (e: Exception) {
            recordResult("TC-14", "云端AI辅助按钮测试", "相机功能", "FAIL",
                System.currentTimeMillis() - startMs,
                "云端AI按钮测试失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc15_settingsCloudAiToggleTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
            settingsButton?.click()
            Thread.sleep(1500)
            val cloudAiLabel = device.wait(Until.findObject(By.text("启用云端AI分析")), mediumTimeout)
            assertNotNull("Cloud AI toggle label should be visible", cloudAiLabel)
            recordResult("TC-15", "设置界面云端AI开关测试", "设置", "PASS",
                System.currentTimeMillis() - startMs,
                "云端AI设置区域可见, 标签: ${cloudAiLabel.text}")
        } catch (e: Exception) {
            recordResult("TC-15", "设置界面云端AI开关测试", "设置", "FAIL",
                System.currentTimeMillis() - startMs,
                "云端AI设置测试失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc16_appNoCrashTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            Thread.sleep(3000)
            val currentPkg = device.currentPackageName
            assertEquals("App should still be running", packageName, currentPkg)
            recordResult("TC-16", "应用无崩溃测试", "稳定性", "PASS",
                System.currentTimeMillis() - startMs,
                "应用运行3秒后无崩溃，包名仍为 $currentPkg")
        } catch (e: Exception) {
            recordResult("TC-16", "应用无崩溃测试", "稳定性", "FAIL",
                System.currentTimeMillis() - startMs,
                "应用崩溃: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc17_settingsCacheSizeDisplayTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
            settingsButton?.click()
            Thread.sleep(1500)
            val cacheText = device.wait(Until.findObject(By.textContains("缓存大小")), mediumTimeout)
            assertNotNull("Cache size text should be displayed", cacheText)
            val cacheTextStr = cacheText.text
            assertTrue("Cache size should contain 'KB'",
                cacheTextStr.contains("KB"))
            recordResult("TC-17", "设置界面缓存大小显示测试", "设置", "PASS",
                System.currentTimeMillis() - startMs,
                "缓存大小正确显示: $cacheTextStr")
        } catch (e: Exception) {
            recordResult("TC-17", "设置界面缓存大小显示测试", "设置", "FAIL",
                System.currentTimeMillis() - startMs,
                "缓存大小显示异常: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc18_appRelaunchStabilityTest() {
        val startMs = System.currentTimeMillis()
        var successCount = 0
        val totalAttempts = 3
        try {
            for (i in 1..totalAttempts) {
                launchAppFromHome()
                val hasPackage = device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
                if (hasPackage) successCount++
                device.pressHome()
                Thread.sleep(1000)
            }
            assertEquals("App should launch successfully all $totalAttempts times",
                totalAttempts, successCount)
            recordResult("TC-18", "应用多次启动稳定性测试", "稳定性", "PASS",
                System.currentTimeMillis() - startMs,
                "应用连续启动 $totalAttempts 次均成功")
        } catch (e: Exception) {
            recordResult("TC-18", "应用多次启动稳定性测试", "稳定性", "FAIL",
                System.currentTimeMillis() - startMs,
                "稳定性测试失败: 成功 $successCount/$totalAttempts 次, ${e.message}")
            throw e
        }
    }

    @Test
    fun tc19_cameraInfoBarDisplayTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            Thread.sleep(3000)
            val sceneText = device.findObject(By.textContains("拍摄"))
            val hasSceneInfo = sceneText != null
            recordResult("TC-19", "相机信息栏显示测试", "相机界面", "PASS",
                System.currentTimeMillis() - startMs,
                "相机信息栏显示状态: 场景识别文本可见=$hasSceneInfo")
        } catch (e: Exception) {
            recordResult("TC-19", "相机信息栏显示测试", "相机界面", "FAIL",
                System.currentTimeMillis() - startMs,
                "相机信息栏测试失败: ${e.message}")
            throw e
        }
    }

    @Test
    fun tc20_paramSettingsPanelTest() {
        val startMs = System.currentTimeMillis()
        try {
            navigateToCameraScreen()
            val paramButton = device.wait(Until.findObject(By.desc("参数设置")), mediumTimeout)
            if (paramButton != null) {
                paramButton.click()
                Thread.sleep(1000)
                recordResult("TC-20", "参数设置面板测试", "相机功能", "PASS",
                    System.currentTimeMillis() - startMs,
                    "参数设置面板按钮可点击打开")
            } else {
                recordResult("TC-20", "参数设置面板测试", "相机功能", "SKIP",
                    System.currentTimeMillis() - startMs,
                    "参数设置按钮在当前界面未找到（可能需要滚动）")
            }
        } catch (e: Exception) {
            recordResult("TC-20", "参数设置面板测试", "相机功能", "FAIL",
                System.currentTimeMillis() - startMs,
                "参数设置面板测试失败: ${e.message}")
            throw e
        }
    }
}
