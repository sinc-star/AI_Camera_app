package com.aicamera.app

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.lang.ProcessBuilder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AiCameraPerformanceTest {

    private lateinit var device: UiDevice
    private val packageName = "com.aicamera.app"
    private val launchTimeout = 15000L
    private val mediumTimeout = 5000L

    private val performanceResults = mutableListOf<PerformanceResult>()

    data class PerformanceResult(
        val testCaseId: String,
        val testName: String,
        val metric: String,
        val value: Double,
        val unit: String,
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
        writePerformanceResultsToFile()
    }

    private fun grantPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant $packageName android.permission.CAMERA")
        instrumentation.uiAutomation.executeShellCommand("pm grant $packageName android.permission.READ_MEDIA_IMAGES")
        Thread.sleep(300)
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

    private fun getMemoryUsage(): Double {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = readShellCommandOutput(
            instrumentation.uiAutomation.executeShellCommand(
                "dumpsys meminfo $packageName | grep -A 5 'TOTAL PSS'"
            )
        )
        val lines = output.split("\n")
        for (line in lines) {
            if (line.contains("TOTAL PSS")) {
                val parts = line.trim().split("\\s+")
                if (parts.size >= 2) {
                    return parts[1].toDouble()
                }
            }
        }
        return 0.0
    }

    private fun getCpuUsage(): Double {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = readShellCommandOutput(
            instrumentation.uiAutomation.executeShellCommand(
                "top -n 1 -d 1 | grep $packageName"
            )
        )
        val lines = output.split("\n")
        for (line in lines) {
            if (line.contains(packageName)) {
                val parts = line.trim().split("\\s+")
                if (parts.size >= 9) {
                    return parts[8].toDoubleOrNull() ?: 0.0
                }
            }
        }
        return 0.0
    }

    private fun getBatteryUsage(): Double {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = readShellCommandOutput(
            instrumentation.uiAutomation.executeShellCommand(
                "dumpsys battery | grep level"
            )
        )
        val lines = output.split("\n")
        for (line in lines) {
            if (line.contains("level:")) {
                val parts = line.trim().split(":")
                if (parts.size >= 2) {
                    return parts[1].trim().toDouble()
                }
            }
        }
        return 0.0
    }

    private fun readShellCommandOutput(pfd: android.os.ParcelFileDescriptor): String {
        val reader = java.io.BufferedReader(
            java.io.InputStreamReader(
                java.io.FileInputStream(pfd.fileDescriptor)
            )
        )
        val sb = java.lang.StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        pfd.close()
        return sb.toString()
    }

    private fun recordPerformanceResult(
        testCaseId: String,
        testName: String,
        metric: String,
        value: Double,
        unit: String
    ) {
        performanceResults.add(
            PerformanceResult(
                testCaseId = testCaseId,
                testName = testName,
                metric = metric,
                value = value,
                unit = unit,
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(java.util.Date())
            )
        )
    }

    private fun writePerformanceResultsToFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resultsDir = File(context.getExternalFilesDir(null), "performance_results")
        resultsDir.mkdirs()
        val resultsFile = File(resultsDir, "performance_results_${System.currentTimeMillis()}.txt")

        val sb = StringBuilder()
        sb.appendLine("AI Camera Performance Test Results")
        sb.appendLine("=".repeat(70))
        sb.appendLine("Device: ${device.productName}")
        sb.appendLine("Android API: ${android.os.Build.VERSION.SDK_INT}")
        sb.appendLine("Package: $packageName")
        sb.appendLine("Test Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        sb.appendLine("=".repeat(70))
        sb.appendLine()

        performanceResults.forEach { r ->
            sb.appendLine("${r.testCaseId}: ${r.testName}")
            sb.appendLine("  Metric: ${r.metric}")
            sb.appendLine("  Value: ${r.value} ${r.unit}")
            sb.appendLine("  Time: ${r.timestamp}")
            sb.appendLine()
        }

        sb.appendLine("=".repeat(70))
        sb.appendLine("Total Performance Metrics: ${performanceResults.size}")
        sb.appendLine("=".repeat(70))

        resultsFile.writeText(sb.toString())
    }

    @Test
    fun tc01_appStartupTimeTest() {
        val startTime = System.currentTimeMillis()
        launchAppFromHome()
        val endTime = System.currentTimeMillis()
        val startupTime = endTime - startTime
        recordPerformanceResult("TC-01", "应用启动时间测试", "启动时间", startupTime.toDouble(), "ms")
        assertTrue("Startup time should be less than 5 seconds", startupTime < 5000)
    }

    @Test
    fun tc02_memoryUsageTest() {
        launchAppFromHome()
        Thread.sleep(2000)
        val memoryUsage = getMemoryUsage()
        recordPerformanceResult("TC-02", "内存占用测试", "PSS内存", memoryUsage, "MB")
        assertTrue("Memory usage should be less than 500MB", memoryUsage < 500)
    }

    @Test
    fun tc03_cpuUsageTest() {
        launchAppFromHome()
        Thread.sleep(2000)
        val cpuUsage = getCpuUsage()
        recordPerformanceResult("TC-03", "CPU占用测试", "CPU使用率", cpuUsage, "%")
        assertTrue("CPU usage should be less than 50%", cpuUsage < 50.0)
    }

    @Test
    fun tc04_batteryUsageTest() {
        val initialBattery = getBatteryUsage()
        launchAppFromHome()
        Thread.sleep(3000)
        val finalBattery = getBatteryUsage()
        val batteryDiff = initialBattery - finalBattery
        recordPerformanceResult("TC-04", "电池消耗测试", "电池消耗", batteryDiff, "%")
        assertTrue("Battery usage should be less than 5%", batteryDiff < 5.0)
    }

    @Test
    fun tc05_cameraPreviewLaunchTime() {
        launchAppFromHome()
        val startTime = System.currentTimeMillis()
        val launchButton = device.findObject(By.text("点击启动"))
        if (launchButton != null) {
            launchButton.click()
            Thread.sleep(3000)
            val endTime = System.currentTimeMillis()
            val previewTime = endTime - startTime
            recordPerformanceResult("TC-05", "相机预览启动时间", "预览启动时间", previewTime.toDouble(), "ms")
            assertTrue("Preview launch time should be less than 3 seconds", previewTime < 3000)
        }
    }

    @Test
    fun tc06_settingsNavigationTime() {
        launchAppFromHome()
        val launchButton = device.findObject(By.text("点击启动"))
        if (launchButton != null) {
            launchButton.click()
            Thread.sleep(3000)
            val startTime = System.currentTimeMillis()
            val settingsButton = device.findObject(By.desc("设置"))
            settingsButton?.click()
            Thread.sleep(1500)
            val endTime = System.currentTimeMillis()
            val navTime = endTime - startTime
            recordPerformanceResult("TC-06", "设置页面导航时间", "导航时间", navTime.toDouble(), "ms")
            assertTrue("Navigation time should be less than 2 seconds", navTime < 2000)
        }
    }

    @Test
    fun tc07_appRelaunchTime() {
        launchAppFromHome()
        device.pressHome()
        Thread.sleep(1000)
        val startTime = System.currentTimeMillis()
        launchAppFromHome()
        val endTime = System.currentTimeMillis()
        val relaunchTime = endTime - startTime
        recordPerformanceResult("TC-07", "应用重新启动时间", "重新启动时间", relaunchTime.toDouble(), "ms")
        assertTrue("Relaunch time should be less than 3 seconds", relaunchTime < 3000)
    }

    @Test
    fun tc08_flashToggleResponseTime() {
        launchAppFromHome()
        val launchButton = device.findObject(By.text("点击启动"))
        if (launchButton != null) {
            launchButton.click()
            Thread.sleep(3000)
            val flashButton = device.findObject(By.desc("闪光灯"))
            if (flashButton != null) {
                val startTime = System.currentTimeMillis()
                flashButton.click()
                Thread.sleep(500)
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                recordPerformanceResult("TC-08", "闪光灯切换响应时间", "响应时间", responseTime.toDouble(), "ms")
                assertTrue("Response time should be less than 1 second", responseTime < 1000)
            }
        }
    }

    @Test
    fun tc09_memoryStabilityTest() {
        launchAppFromHome()
        val initialMemory = getMemoryUsage()
        Thread.sleep(5000)
        val finalMemory = getMemoryUsage()
        val memoryDiff = finalMemory - initialMemory
        recordPerformanceResult("TC-09", "内存稳定性测试", "内存增长", memoryDiff, "MB")
        assertTrue("Memory growth should be less than 50MB", memoryDiff < 50.0)
    }

    @Test
    fun tc10_cameraFlipResponseTime() {
        launchAppFromHome()
        val launchButton = device.findObject(By.text("点击启动"))
        if (launchButton != null) {
            launchButton.click()
            Thread.sleep(3000)
            val flipButton = device.findObject(By.desc("反转摄像头"))
            if (flipButton != null) {
                val startTime = System.currentTimeMillis()
                flipButton.click()
                Thread.sleep(1500)
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                recordPerformanceResult("TC-10", "摄像头翻转响应时间", "响应时间", responseTime.toDouble(), "ms")
                assertTrue("Response time should be less than 2 seconds", responseTime < 2000)
            }
        }
    }
}
