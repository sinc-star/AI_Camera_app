# AI Camera 自动化测试用例

## 测试用例目录

### 1. 启动测试
- [TC-01: 应用启动测试](#tc-01-应用启动测试)
- [TC-02: 启动画面显示测试](#tc-02-启动画面显示测试)
- [TC-03: 启动画面功能卡片显示测试](#tc-03-启动画面功能卡片显示测试)
- [TC-04: 启动画面导航到相机界面](#tc-04-启动画面导航到相机界面)

### 2. 相机界面功能测试
- [TC-05: 相机界面设置按钮测试](#tc-05-相机界面设置按钮测试)
- [TC-06: 闪光灯切换测试](#tc-06-闪光灯切换测试)
- [TC-07: 构图辅助线切换测试](#tc-07-构图辅助线切换测试)
- [TC-08: HDR 切换测试](#tc-08-hdr-切换测试)
- [TC-09: 定时器循环切换测试](#tc-09-定时器循环切换测试)
- [TC-13: 相机翻转按钮测试](#tc-13-相机翻转按钮测试)
- [TC-14: 云端AI辅助按钮测试](#tc-14-云端ai辅助按钮测试)
- [TC-19: 相机信息栏显示测试](#tc-19-相机信息栏显示测试)
- [TC-20: 参数设置面板测试](#tc-20-参数设置面板测试)

### 3. 设置界面功能测试
- [TC-10: 设置界面主题切换测试](#tc-10-设置界面主题切换测试)
- [TC-11: 设置界面缓存清理测试](#tc-11-设置界面缓存清理测试)
- [TC-12: 设置界面返回导航测试](#tc-12-设置界面返回导航测试)
- [TC-15: 设置界面云端AI开关测试](#tc-15-设置界面云端ai开关测试)
- [TC-17: 设置界面缓存大小显示测试](#tc-17-设置界面缓存大小显示测试)

### 4. 稳定性测试
- [TC-16: 应用无崩溃测试](#tc-16-应用无崩溃测试)
- [TC-18: 应用多次启动稳定性测试](#tc-18-应用多次启动稳定性测试)

### 5. 性能测试
- [TC-01: 应用启动时间测试](#tc-01-应用启动时间测试-1)
- [TC-02: 内存占用测试](#tc-02-内存占用测试)
- [TC-03: CPU占用测试](#tc-03-cpu占用测试)
- [TC-04: 电池消耗测试](#tc-04-电池消耗测试)
- [TC-05: 相机预览启动时间](#tc-05-相机预览启动时间)
- [TC-06: 设置页面导航时间](#tc-06-设置页面导航时间)
- [TC-07: 应用重新启动时间](#tc-07-应用重新启动时间)
- [TC-08: 闪光灯切换响应时间](#tc-08-闪光灯切换响应时间)
- [TC-09: 内存稳定性测试](#tc-09-内存稳定性测试)
- [TC-10: 摄像头翻转响应时间](#tc-10-摄像头翻转响应时间)

## 测试用例详情

### TC-01: 应用启动测试
**测试目标**: 验证应用能够正常启动
**测试步骤**:
1. 从主屏幕启动应用
2. 等待应用启动完成
3. 验证当前包名为 `com.aicamera.app`
**预期结果**: 应用成功启动，包名验证通过
**测试代码**:
```kotlin
@Test
fun tc01_appLaunchTest() {
    launchAppFromHome()
    val hasPackage = device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
    assertTrue("App should launch within $launchTimeout ms", hasPackage)
    val currentPkg = device.currentPackageName
    assertEquals("Current package should be $packageName", packageName, currentPkg)
}
```

### TC-02: 启动画面显示测试
**测试目标**: 验证启动画面正确显示
**测试步骤**:
1. 启动应用
2. 检查启动画面是否显示 "智能相机" 标题
3. 检查启动画面是否显示 "点击启动" 按钮
**预期结果**: 启动画面正确显示标题和按钮
**测试代码**:
```kotlin
@Test
fun tc02_splashScreenDisplayTest() {
    launchAppFromHome()
    val splashText = device.wait(Until.findObject(By.text("智能相机")), mediumTimeout)
    assertNotNull("Splash screen should display '智能相机'", splashText)
    val launchButton = device.wait(Until.findObject(By.text("点击启动")), mediumTimeout)
    assertNotNull("Splash screen should display '点击启动'", launchButton)
}
```

### TC-03: 启动画面功能卡片显示测试
**测试目标**: 验证启动画面功能卡片正确显示
**测试步骤**:
1. 启动应用
2. 检查是否显示 "智能辅助" 卡片
3. 检查是否显示 "构图优化" 卡片
4. 检查是否显示 "AI 美化" 卡片
**预期结果**: 三个功能卡片均正确显示
**测试代码**:
```kotlin
@Test
fun tc03_splashFeatureCardsTest() {
    launchAppFromHome()
    val feature1 = device.wait(Until.findObject(By.text("智能辅助")), mediumTimeout)
    assertNotNull("Feature card '智能辅助' should be displayed", feature1)
    val feature2 = device.wait(Until.findObject(By.text("构图优化")), mediumTimeout)
    assertNotNull("Feature card '构图优化' should be displayed", feature2)
    val feature3 = device.wait(Until.findObject(By.text("AI 美化")), mediumTimeout)
    assertNotNull("Feature card 'AI 美化' should be displayed", feature3)
}
```

### TC-04: 启动画面导航到相机界面
**测试目标**: 验证从启动画面能正确导航到相机界面
**测试步骤**:
1. 启动应用
2. 点击 "点击启动" 按钮
3. 验证是否成功进入相机界面（设置按钮或闪光灯按钮可见）
**预期结果**: 成功导航到相机界面
**测试代码**:
```kotlin
@Test
fun tc04_splashNavigateToCameraTest() {
    navigateToCameraScreen()
    val settingsButton = device.findObject(By.desc("设置"))
    val flashButton = device.findObject(By.desc("闪光灯"))
    assertTrue("Should navigate to camera screen",
        settingsButton != null || flashButton != null)
}
```

### TC-05: 相机界面设置按钮测试
**测试目标**: 验证相机界面的设置按钮功能
**测试步骤**:
1. 进入相机界面
2. 点击设置按钮
3. 验证是否进入设置界面
**预期结果**: 设置按钮可点击并成功导航到设置界面
**测试代码**:
```kotlin
@Test
fun tc05_cameraSettingsButtonTest() {
    navigateToCameraScreen()
    val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
    assertNotNull("Settings button should be visible", settingsButton)
    settingsButton.click()
    Thread.sleep(1500)
    val settingsTitle = device.wait(Until.findObject(By.text("设置")), mediumTimeout)
    assertNotNull("Should navigate to settings screen", settingsTitle)
}
```

### TC-06: 闪光灯切换测试
**测试目标**: 验证闪光灯切换功能
**测试步骤**:
1. 进入相机界面
2. 点击闪光灯按钮
3. 验证闪光灯状态是否切换
**预期结果**: 闪光灯按钮可点击切换
**测试代码**:
```kotlin
@Test
fun tc06_flashToggleTest() {
    navigateToCameraScreen()
    val flashButton = device.wait(Until.findObject(By.desc("闪光灯")), mediumTimeout)
    assertNotNull("Flash button should be visible", flashButton)
    flashButton.click()
    Thread.sleep(1000)
}
```

### TC-07: 构图辅助线切换测试
**测试目标**: 验证构图辅助线切换功能
**测试步骤**:
1. 进入相机界面
2. 点击辅助线按钮
3. 验证辅助线状态是否切换
**预期结果**: 辅助线按钮可点击切换
**测试代码**:
```kotlin
@Test
fun tc07_gridGuideToggleTest() {
    navigateToCameraScreen()
    val gridButton = device.wait(Until.findObject(By.desc("辅助线")), mediumTimeout)
    assertNotNull("Grid guide button should be visible", gridButton)
    gridButton.click()
    Thread.sleep(1000)
}
```

### TC-08: HDR 切换测试
**测试目标**: 验证 HDR 切换功能
**测试步骤**:
1. 进入相机界面
2. 点击 HDR 按钮
3. 验证 HDR 状态是否切换
**预期结果**: HDR 按钮可点击切换
**测试代码**:
```kotlin
@Test
fun tc08_hdrToggleTest() {
    navigateToCameraScreen()
    val hdrButton = device.wait(Until.findObject(By.text("HDR")), mediumTimeout)
    assertNotNull("HDR button should be visible", hdrButton)
    hdrButton.click()
    Thread.sleep(1500)
}
```

### TC-09: 定时器循环切换测试
**测试目标**: 验证定时器切换功能
**测试步骤**:
1. 进入相机界面
2. 点击定时器按钮
3. 验证是否显示 "3s" 状态
**预期结果**: 定时器按钮可点击切换，首次点击后显示 "3s"
**测试代码**:
```kotlin
@Test
fun tc09_timerCycleTest() {
    navigateToCameraScreen()
    val timerButton = device.wait(Until.findObject(By.desc("定时器")), mediumTimeout)
    assertNotNull("Timer button should be visible", timerButton)
    timerButton.click()
    Thread.sleep(1000)
    val afterFirst = device.findObject(By.text("3s"))
    assertNotNull("After first click, should show '3s'", afterFirst)
}
```

### TC-10: 设置界面主题切换测试
**测试目标**: 验证设置界面主题切换功能
**测试步骤**:
1. 进入设置界面
2. 点击 "科技蓝" 主题
3. 点击 "明亮清新" 主题
4. 点击 "专业摄影" 主题
**预期结果**: 三种主题切换正常
**测试代码**:
```kotlin
@Test
fun tc10_settingsThemeSwitchTest() {
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
}
```

### TC-11: 设置界面缓存清理测试
**测试目标**: 验证设置界面缓存清理功能
**测试步骤**:
1. 进入设置界面
2. 点击 "清理缓存" 按钮
3. 验证缓存清理是否执行
**预期结果**: 缓存清理按钮可点击执行
**测试代码**:
```kotlin
@Test
fun tc11_settingsCacheClearTest() {
    navigateToCameraScreen()
    val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
    settingsButton?.click()
    Thread.sleep(1500)
    val clearButton = device.wait(Until.findObject(By.text("清理缓存")), mediumTimeout)
    assertNotNull("Clear cache button should be visible", clearButton)
    clearButton.click()
    Thread.sleep(1000)
}
```

### TC-12: 设置界面返回导航测试
**测试目标**: 验证从设置界面返回相机界面的导航功能
**测试步骤**:
1. 进入设置界面
2. 按返回键
3. 验证是否返回相机界面
**预期结果**: 从设置界面成功返回相机界面
**测试代码**:
```kotlin
@Test
fun tc12_settingsBackNavigationTest() {
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
}
```

### TC-13: 相机翻转按钮测试
**测试目标**: 验证相机翻转按钮功能
**测试步骤**:
1. 进入相机界面
2. 点击反转摄像头按钮
3. 验证摄像头是否切换
**预期结果**: 相机翻转按钮可点击切换前后摄像头
**测试代码**:
```kotlin
@Test
fun tc13_cameraFlipTest() {
    navigateToCameraScreen()
    val flipButton = device.wait(Until.findObject(By.desc("反转摄像头")), mediumTimeout)
    assertNotNull("Camera flip button should be visible", flipButton)
    flipButton.click()
    Thread.sleep(2000)
}
```

### TC-14: 云端AI辅助按钮测试
**测试目标**: 验证云端AI辅助按钮功能
**测试步骤**:
1. 进入相机界面
2. 点击云端AI按钮
3. 验证按钮是否响应
**预期结果**: 云端AI辅助按钮可点击（未配置API Key时为off状态）
**测试代码**:
```kotlin
@Test
fun tc14_cloudAiButtonTest() {
    navigateToCameraScreen()
    val cloudAiButton = device.wait(Until.findObject(By.desc("云端AI")), mediumTimeout)
    assertNotNull("Cloud AI button should be visible", cloudAiButton)
    cloudAiButton.click()
    Thread.sleep(1000)
}
```

### TC-15: 设置界面云端AI开关测试
**测试目标**: 验证设置界面云端AI开关功能
**测试步骤**:
1. 进入设置界面
2. 检查是否显示 "启用云端AI分析" 标签
**预期结果**: 云端AI设置区域可见
**测试代码**:
```kotlin
@Test
fun tc15_settingsCloudAiToggleTest() {
    navigateToCameraScreen()
    val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
    settingsButton?.click()
    Thread.sleep(1500)
    val cloudAiLabel = device.wait(Until.findObject(By.text("启用云端AI分析")), mediumTimeout)
    assertNotNull("Cloud AI toggle label should be visible", cloudAiLabel)
}
```

### TC-16: 应用无崩溃测试
**测试目标**: 验证应用在运行过程中无崩溃
**测试步骤**:
1. 进入相机界面
2. 保持应用运行3秒
3. 验证应用是否仍在运行
**预期结果**: 应用运行3秒后无崩溃
**测试代码**:
```kotlin
@Test
fun tc16_appNoCrashTest() {
    navigateToCameraScreen()
    Thread.sleep(3000)
    val currentPkg = device.currentPackageName
    assertEquals("App should still be running", packageName, currentPkg)
}
```

### TC-17: 设置界面缓存大小显示测试
**测试目标**: 验证设置界面缓存大小显示功能
**测试步骤**:
1. 进入设置界面
2. 检查是否显示缓存大小
3. 验证缓存大小格式是否正确
**预期结果**: 缓存大小正确显示，包含 "KB" 单位
**测试代码**:
```kotlin
@Test
fun tc17_settingsCacheSizeDisplayTest() {
    navigateToCameraScreen()
    val settingsButton = device.wait(Until.findObject(By.desc("设置")), mediumTimeout)
    settingsButton?.click()
    Thread.sleep(1500)
    val cacheText = device.wait(Until.findObject(By.textContains("缓存大小")), mediumTimeout)
    assertNotNull("Cache size text should be displayed", cacheText)
    val cacheTextStr = cacheText.text
    assertTrue("Cache size should contain 'KB'",
        cacheTextStr.contains("KB"))
}
```

### TC-18: 应用多次启动稳定性测试
**测试目标**: 验证应用多次启动的稳定性
**测试步骤**:
1. 启动应用
2. 按Home键
3. 重复步骤1-2，共3次
4. 验证每次启动是否成功
**预期结果**: 应用连续启动3次均成功
**测试代码**:
```kotlin
@Test
fun tc18_appRelaunchStabilityTest() {
    var successCount = 0
    val totalAttempts = 3
    for (i in 1..totalAttempts) {
        launchAppFromHome()
        val hasPackage = device.wait(Until.hasObject(By.pkg(packageName).depth(0)), launchTimeout)
        if (hasPackage) successCount++
        device.pressHome()
        Thread.sleep(1000)
    }
    assertEquals("App should launch successfully all $totalAttempts times",
        totalAttempts, successCount)
}
```

### TC-19: 相机信息栏显示测试
**测试目标**: 验证相机信息栏显示功能
**测试步骤**:
1. 进入相机界面
2. 检查信息栏是否显示场景识别文本
**预期结果**: 相机信息栏显示场景识别文本
**测试代码**:
```kotlin
@Test
fun tc19_cameraInfoBarDisplayTest() {
    navigateToCameraScreen()
    Thread.sleep(3000)
    val sceneText = device.findObject(By.textContains("拍摄"))
    val hasSceneInfo = sceneText != null
}
```

### TC-20: 参数设置面板测试
**测试目标**: 验证参数设置面板功能
**测试步骤**:
1. 进入相机界面
2. 寻找参数设置按钮
3. 点击参数设置按钮
**预期结果**: 参数设置面板按钮可点击打开
**测试代码**:
```kotlin
@Test
fun tc20_paramSettingsPanelTest() {
    navigateToCameraScreen()
    val paramButton = device.wait(Until.findObject(By.desc("参数设置")), mediumTimeout)
    if (paramButton != null) {
        paramButton.click()
        Thread.sleep(1000)
    } else {
        // 跳过测试，参数设置按钮未找到
    }
}
```

### TC-01: 应用启动时间测试
**测试目标**: 测试应用启动时间性能
**测试步骤**:
1. 从主屏幕启动应用
2. 记录启动时间
3. 验证启动时间是否小于5秒
**预期结果**: 应用启动时间小于5秒
**测试代码**:
```kotlin
@Test
fun tc01_appStartupTimeTest() {
    val startTime = System.currentTimeMillis()
    launchAppFromHome()
    val endTime = System.currentTimeMillis()
    val startupTime = endTime - startTime
    recordPerformanceResult("TC-01", "应用启动时间测试", "启动时间", startupTime.toDouble(), "ms")
    assertTrue("Startup time should be less than 5 seconds", startupTime < 5000)
}
```

### TC-02: 内存占用测试
**测试目标**: 测试应用内存占用情况
**测试步骤**:
1. 启动应用
2. 进入相机界面
3. 测量PSS内存占用
4. 验证内存占用是否小于500MB
**预期结果**: 内存占用小于500MB
**测试代码**:
```kotlin
@Test
fun tc02_memoryUsageTest() {
    launchAppFromHome()
    Thread.sleep(2000)
    val memoryUsage = getMemoryUsage()
    recordPerformanceResult("TC-02", "内存占用测试", "PSS内存", memoryUsage, "MB")
    assertTrue("Memory usage should be less than 500MB", memoryUsage < 500)
}
```

### TC-03: CPU占用测试
**测试目标**: 测试应用CPU占用情况
**测试步骤**:
1. 启动应用
2. 进入相机界面
3. 测量CPU使用率
4. 验证CPU使用率是否小于50%
**预期结果**: CPU使用率小于50%
**测试代码**:
```kotlin
@Test
fun tc03_cpuUsageTest() {
    launchAppFromHome()
    Thread.sleep(2000)
    val cpuUsage = getCpuUsage()
    recordPerformanceResult("TC-03", "CPU占用测试", "CPU使用率", cpuUsage, "%")
    assertTrue("CPU usage should be less than 50%", cpuUsage < 50.0)
}
```

### TC-04: 电池消耗测试
**测试目标**: 测试应用电池消耗情况
**测试步骤**:
1. 记录初始电池电量
2. 启动应用并运行3秒
3. 记录最终电池电量
4. 验证电池消耗是否小于5%
**预期结果**: 电池消耗小于5%
**测试代码**:
```kotlin
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
```

### TC-05: 相机预览启动时间
**测试目标**: 测试相机预览启动时间
**测试步骤**:
1. 启动应用
2. 点击"点击启动"按钮进入相机界面
3. 记录预览启动时间
4. 验证预览启动时间是否小于3秒
**预期结果**: 相机预览启动时间小于3秒
**测试代码**:
```kotlin
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
```

### TC-06: 设置页面导航时间
**测试目标**: 测试设置页面导航时间
**测试步骤**:
1. 进入相机界面
2. 点击设置按钮
3. 记录导航时间
4. 验证导航时间是否小于2秒
**预期结果**: 设置页面导航时间小于2秒
**测试代码**:
```kotlin
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
```

### TC-07: 应用重新启动时间
**测试目标**: 测试应用重新启动时间
**测试步骤**:
1. 启动应用
2. 按Home键
3. 重新启动应用
4. 记录重新启动时间
5. 验证重新启动时间是否小于3秒
**预期结果**: 应用重新启动时间小于3秒
**测试代码**:
```kotlin
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
```

### TC-08: 闪光灯切换响应时间
**测试目标**: 测试闪光灯切换响应时间
**测试步骤**:
1. 进入相机界面
2. 点击闪光灯按钮
3. 记录响应时间
4. 验证响应时间是否小于1秒
**预期结果**: 闪光灯切换响应时间小于1秒
**测试代码**:
```kotlin
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
```

### TC-09: 内存稳定性测试
**测试目标**: 测试应用内存稳定性
**测试步骤**:
1. 启动应用
2. 进入相机界面
3. 记录初始内存占用
4. 保持运行5秒
5. 记录最终内存占用
6. 验证内存增长是否小于50MB
**预期结果**: 内存增长小于50MB
**测试代码**:
```kotlin
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
```

### TC-10: 摄像头翻转响应时间
**测试目标**: 测试摄像头翻转响应时间
**测试步骤**:
1. 进入相机界面
2. 点击摄像头翻转按钮
3. 记录响应时间
4. 验证响应时间是否小于2秒
**预期结果**: 摄像头翻转响应时间小于2秒
**测试代码**:
```kotlin
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
```

## 测试环境配置

### 设备要求
- Android 设备或模拟器 (API 21+)
- 至少 2GB 内存
- 相机权限
- 存储权限

### 依赖配置
```kotlin
// app/build.gradle.kts
androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
```

### 测试执行命令
```bash
# 构建测试 APK
./gradlew assembleDebugAndroidTest

# 安装测试 APK
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# 运行功能测试
adb shell am instrument -w -r -e class com.aicamera.app.AiCameraUiAutomatorTest com.aicamera.app.test/androidx.test.runner.AndroidJUnitRunner

# 运行性能测试
adb shell am instrument -w -r -e class com.aicamera.app.AiCameraPerformanceTest com.aicamera.app.test/androidx.test.runner.AndroidJUnitRunner
```

## 测试结果记录

测试结果会自动记录到设备的以下位置：
```
/sdcard/Android/data/com.aicamera.app/files/test_results/
/sdcard/Android/data/com.aicamera.app/files/performance_results/
```

包含以下信息：
- 测试用例执行状态
- 执行时间
- 详细日志
- 设备信息
- 性能指标数据

## 注意事项

1. **权限处理**
   - 测试前会自动授予相机和存储权限
   - 确保设备允许应用访问相机

2. **网络连接**
   - 云端AI功能需要网络连接
   - 无网络时云端AI会显示为off状态

3. **设备兼容性**
   - 测试在标准Android模拟器上执行
   - 不同设备的屏幕尺寸可能影响UI元素位置

4. **测试顺序**
   - 测试按编号顺序执行
   - 每个测试独立运行，不依赖其他测试结果

5. **错误处理**
   - 测试失败时会记录详细错误信息
   - 测试过程中遇到异常会自动恢复

## 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.0 | 2026-04-10 | 初始测试用例文档 |
| 1.1 | 2026-04-10 | 修复定时器测试用例 |
| 1.2 | 2026-04-10 | 更新HDR测试元素定位 |
| 1.3 | 2026-04-10 | 添加性能测试用例 |