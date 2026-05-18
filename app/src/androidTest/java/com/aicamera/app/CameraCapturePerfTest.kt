package com.aicamera.app

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证 CameraBackend.capturePhoto 修复效果：
 *   - postProcess 是否真正在后台线程执行（非 main 线程）
 *   - takePicture 回调是否在后台线程执行
 *   - 总耗时是否在可接受范围内
 *
 * 查看 PerfTracer 日志：adb logcat -s PerfTracer
 */
@RunWith(AndroidJUnit4::class)
class CameraCapturePerfTest {

    private lateinit var device: UiDevice
    private val packageName = "com.aicamera.app"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation = instrumentation.uiAutomation

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        grantPermissions()
    }

    private fun grantPermissions() {
        uiAutomation.executeShellCommand("pm grant $packageName android.permission.CAMERA").close()
        uiAutomation.executeShellCommand("pm grant $packageName android.permission.READ_MEDIA_IMAGES").close()
        Thread.sleep(300)
    }

    private fun launchAppFromHome() {
        device.pressHome()
        Thread.sleep(500)
        val context = instrumentation.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("Launch intent should not be null", intent)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 15000)
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

    private fun captureLogcat(): String {
        return ParcelFileDescriptor.AutoCloseInputStream(
            uiAutomation.executeShellCommand("logcat -d -s PerfTracer")
        ).bufferedReader().use { it.readText() }
    }

    private fun clearLogcat() {
        uiAutomation.executeShellCommand("logcat -c").close()
    }

    private fun shellLog(msg: String) {
        uiAutomation.executeShellCommand("log -p d -t PerfTracer 'TEST: $msg'").close()
    }

    private fun extractThreadInfo(line: String): String {
        // dump 格式: [pool-6-thread-1#88] 或 [main#2] — 内容以字母开头且含 #数字
        Regex("\\[([A-Za-z][^\\]]*?#\\d+)\\]").find(line)?.let { return it.groupValues[1] }
        // debug log 格式: thread=main#2
        Regex("thread=([A-Za-z][^\\s|]*)").find(line)?.let { return it.groupValues[1] }
        return ""
    }

    @Test
    fun testCapturePhotoPostProcessingNotOnMainThread() {
        clearLogcat()

        navigateToCameraScreen()

        // 等待相机完全初始化（预览画面稳定）
        Thread.sleep(3000)

        // 找到拍摄按钮并点击
        val captureBtn = device.wait(
            Until.findObject(By.desc("拍照")),
            8000
        )
        assertNotNull("拍摄按钮应在相机界面可见", captureBtn)
        captureBtn.click()

        // 轮询 logcat，等待 PerfTracer dump 输出
        var logOutput = ""
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 25000) {
            Thread.sleep(1500)
            logOutput = captureLogcat()
            if (logOutput.contains("═══")) {
                break
            }
        }

        assertTrue("应捕获到 PerfTracer dump 输出（拍照可能失败但回调应触发）", logOutput.isNotEmpty())

        val lines = logOutput.lines()
        val threadViolations = mutableListOf<String>()
        var postProcessFound = false
        var takePictureFound = false
        var uiClickOnMain = false

        for (line in lines) {
            val threadFull = extractThreadInfo(line)
            if (threadFull.isEmpty()) continue
            val threadBase = threadFull.split("#").firstOrNull() ?: threadFull

            if (line.contains("UI_CLICK") && line.contains("CaptureButton")) {
                uiClickOnMain = threadBase == "main"
            }

            if (line.contains("takePicture") && line.contains("START")) {
                // START 在调用线程（主线程），这是正常的 — 只有 END 必须在后台
                takePictureFound = true
            }

            if (line.contains("takePicture") && line.contains("END")) {
                takePictureFound = true
                if (threadBase == "main") {
                    threadViolations.add("takePicture END 在主线程（未修复！）: $line")
                }
            }

            if (line.contains("postProcess")) {
                postProcessFound = true
                if (threadBase == "main") {
                    threadViolations.add("postProcess 在主线程（未修复！）: $line")
                }
            }
        }

        // UI 点击应在主线程
        assertTrue("CaptureButton UI_CLICK 应在主线程", uiClickOnMain)

        // takePicture 回调必须在后台线程
        if (takePictureFound) {
            assertTrue(
                "takePicture 回调不应在主线程:\n${threadViolations.joinToString("\n")}",
                threadViolations.none { it.contains("takePicture") }
            )
        }

        // postProcess 必须在后台线程
        if (postProcessFound) {
            assertTrue(
                "postProcess 不应在主线程:\n${threadViolations.joinToString("\n")}",
                threadViolations.none { it.contains("postProcess") }
            )
        }

        if (threadViolations.isEmpty()) {
            shellLog("PASS: 所有相机操作均在后台线程")
        } else {
            for (v in threadViolations) {
                shellLog("FAIL: $v")
            }
        }
    }

    @Test
    fun testCapturePhotoTotalLatencyUnderThreshold() {
        clearLogcat()

        navigateToCameraScreen()
        Thread.sleep(3000)

        val captureBtn = device.wait(
            Until.findObject(By.desc("拍照")),
            8000
        )
        assertNotNull("拍摄按钮应在相机界面可见", captureBtn)
        captureBtn.click()

        var logOutput = ""
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 25000) {
            Thread.sleep(1500)
            logOutput = captureLogcat()
            if (logOutput.contains("═══")) {
                break
            }
        }

        assertTrue("应捕获到 PerfTracer dump 输出", logOutput.isNotEmpty())

        val totalDurationMatch = Regex("总耗时:\\s*(\\d+)ms").find(logOutput)
        if (totalDurationMatch != null) {
            val totalMs = totalDurationMatch.groupValues[1].toLong()
            shellLog("Capture total latency: ${totalMs}ms")
            assertTrue(
                "拍照总耗时 ${totalMs}ms 应在合理范围内",
                totalMs < 3000
            )

            val postProcessMatch = Regex("postProcess\\s*=\\s*(\\d+)ms").find(logOutput)
            if (postProcessMatch != null) {
                val postMs = postProcessMatch.groupValues[1].toLong()
                shellLog("postProcess latency: ${postMs}ms")
                assertTrue("postProcess 耗时 ${postMs}ms 应 < 300ms", postMs < 300)
            }
        }
    }

    private fun clickCaptureButton() {
        val btn = device.findObject(By.desc("拍照"))
        if (btn != null) {
            btn.click()
        } else {
            // Compose 拍照后语义树可能重置，回退到坐标点击
            // 拍摄按钮位于屏幕底部中央 (1080x2400 约 540,2250)
            val w = device.displayWidth
            val h = device.displayHeight
            device.click(w / 2, (h * 0.94).toInt())
        }
    }

    @Test
    fun testRepeatedCaptureNoUiFreeze() {
        clearLogcat()

        navigateToCameraScreen()
        Thread.sleep(3000)

        // 连续拍照 3 次，每次都应在后台线程完成
        for (i in 1..3) {
            clearLogcat()

            // 确保相机画面稳定
            Thread.sleep(2000)

            clickCaptureButton()

            // 等待拍照完成（包括回调 + dump）
            Thread.sleep(5000)

            val logOutput = captureLogcat()
            val lines = logOutput.lines()

            var mainThreadViolation = false
            for (line in lines) {
                if (line.contains("UI_CLICK")) continue
                if (line.contains("takePicture") && !line.contains("END")) continue
                if (line.contains("postProcess") ||
                    (line.contains("takePicture") && line.contains("END"))) {
                    val threadFull = extractThreadInfo(line)
                    val threadBase = threadFull.split("#").firstOrNull() ?: threadFull
                    if (threadBase == "main") {
                        mainThreadViolation = true
                        break
                    }
                }
            }

            assertFalse("第 $i 次拍照：takePicture/postProcess 不应在主线程", mainThreadViolation)
        }

        shellLog("PASS: 连续 3 次拍照均未阻塞主线程")
    }
}
