# 相机应用性能瓶颈分析报告

> 基于 PerformanceTracer 埋点的静态代码路径分析
> 分析日期：2026-05-17

---

## 总览：瓶颈严重度排名

| 排名 | 用户感知的卡顿 | 瓶颈操作 | 阻塞线程 | 估算耗时 | 严重度 |
|------|---------------|---------|---------|---------|--------|
| 1 | 拍照后 UI 冻结 | cropToPreviewAspect + mirrorImageHorizontally | 主线程 | 150-600ms | CRITICAL |
| 2 | 比例切换响应延迟 | 200ms 轮询间隔 + Animatable 300ms | 主线程 | 350-500ms | HIGH |
| 3 | 辅助线开启后帧率下降 | captureScaledBitmap GPU 回读 | 主线程 | 每 1.5s 卡 30-100ms | HIGH |
| 4 | 翻转摄像头黑屏过长 | bindToLifecycle CameraX 重绑定 | 主线程 | 100-400ms | MEDIUM |
| 5 | 冷启动慢 | ProcessCameraProvider.getInstance().get() | 主线程 | 50-500ms | MEDIUM |
| 6 | 诊断上报浪费资源 | sendToServer 尝试 5 个不可达 URL | IO线程池 | 最多 20s/次 | LOW |

---

## 路径 1：AspectRatioChange（画幅比例切换）

### 完整调用链

```
👆 UI_CLICK: AspectRatioChange (main#2) +0ms
  |
  +-- 状态写入: previewAspectRatio = newRatio +0ms
  |     \-- 设置 CameraBackend.ManualSettings.previewAspectRatioPortrait
  |
  +-- [等待轮询] 最多延迟 200ms  <-- 瓶颈1
  |     \-- LaunchedEffect while(isActive) { delay(200) }
  |         \-- 检测到 newRatio != previewAspectRatio -> 开始处理
  |
  +-- START: computeTargetBounds +~201ms  (main#2)  耗时: ~1ms
  +-- START: AnimPhase1_Blur     +~203ms  (main#2)  耗时: ~50ms
  +-- START: AnimPhase2_Clear    +~253ms  (main#2)  耗时: ~250ms (两个并行动画)
  +-- START: computeViewfinderBounds  +~504ms  (main#2)  耗时: ~1ms (x2次)
  |
  \-- START: DiagnosticsReport  +~506ms  (main#2)
      \-- withContext(Dispatchers.Default)
          \-- DiagnosticsBackend.report()
              +-- buildJson()   ~1ms
              +-- saveToFile()  ~5-20ms  (IO)
              \-- sendToServer()
                  +-- try URL#1  ~4s 超时  <-- 瓶颈2
                  +-- try URL#2  ~4s 超时
                  +-- try URL#3  ~4s 超时
                  +-- try URL#4  ~4s 超时
                  \-- try URL#5  ~4s 超时

END: 总耗时 ~350-550ms（不含 HTTP 超时部分，该部分在后台 IO 线程）
```

### 瓶颈分析

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 1 | 200ms 轮询间隔 | [CameraScreen.kt:708](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L708) | 用户点击后最多等 200ms 才开始响应。应用使用 `while+delay(200)` 轮询检测比例变化，而非事件驱动 |
| 2 | DiagnosticsReport HTTP 超时 | [DiagnosticsBackend.kt:242-243](app/src/main/java/com/aicamera/app/backend/diagnostics/DiagnosticsBackend.kt#L242) | 虽在 IO 线程，但 5 URL x 4s = 最多 20s 耗尽 IO 线程。**每次比例切换都触发** |
| - | computeViewfinderBounds x3 | [CameraScreen.kt:624,665,673](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L624) | 每次调用约 1ms，x3 可接受，但 4:3 备用值可缓存 |

### 优化建议

1. **消除 200ms 延迟**：用 `snapshotFlow { CameraBackend.ManualSettings.previewAspectRatioPortrait }.collect { }` 替换轮询
2. **生产环境禁用网络诊断**：设置 `NETWORK_ENABLED = false`
3. 缓存 viewfinderBounds43 计算，避免每次比例切换重算

---

## 路径 2：CaptureButton（拍照按钮） CRITICAL

### 完整调用链

```
👆 UI_CLICK: CaptureButton (main#2) +0ms
  |
  +-- [倒计时分支] START: CountdownTimer +0ms  (main#2)  耗时: 3-10s
  |
  \-- CameraBackend.capturePhoto()
      +-- START: takePicture +0ms  (main#2)
      |     \-- imageCapture.takePicture(...)  调度耗时: ~1ms
      |
      \-- [相机异步回调 - 运行在主线程 MainExecutor]
          \-- onImageSaved (main#2) <-- 主线程执行！
              +-- BitmapFactory.decodeFile(photoFile)  ~50-200ms  <-- 瓶颈1
              +-- cropToPreviewAspect()
              |     +-- Bitmap.createBitmap(crop)  ~20-80ms  <-- 瓶颈2
              |     \-- compress(JPEG, 100)  ~100-300ms  <-- 瓶颈3
              +-- mirrorImageHorizontally()
              |     +-- BitmapFactory.decodeFile()  ~50-200ms  <-- 再次解码! 瓶颈4
              |     +-- Bitmap.createBitmap(mirror)  ~20-80ms  <-- 瓶颈5
              |     \-- compress(JPEG, 100)  ~100-300ms  <-- 瓶颈6
              \-- notifyMediaScanner()  ~5-10ms

END: 主线程阻塞 150-600ms（用户看到 UI 冻结）
```

### 瓶颈详解

| # | 操作 | 位置 | 耗时 | 问题 |
|---|------|------|------|------|
| 1 | 全分辨率 JPEG 解码 | [CameraBackend.kt:236](app/src/main/java/com/aicamera/app/backend/camera/CameraBackend.kt#L236) | 50-200ms | 12MP 照片在主线程解码 |
| 2 | 拍照回调 executor 是 Main | [CameraBackend.kt:135](app/src/main/java/com/aicamera/app/backend/camera/CameraBackend.kt#L135) | -- | `ContextCompat.getMainExecutor(context)` 导致整个后处理阻塞主线程 |
| 3 | cropToPreviewAspect + mirrorImageHorizontally 串行 | [CameraBackend.kt:139-151](app/src/main/java/com/aicamera/app/backend/camera/CameraBackend.kt#L139) | 150-600ms | 非 4:3 比例且前置摄像头时，**对同一文件做两次 decodeFile** |

### 优化建议

1. `takePicture` 的回调 executor 改为 IO 线程，仅在 `onSuccess` 最终回调切回主线程
2. `BitmapFactory.Options.inSampleSize = 2` 降采样再处理
3. `compress(JPEG, 100)` 改为 `compress(JPEG, 95)`（几乎无损，快 30-50%）
4. `cropToPreviewAspect` 和 `mirrorImageHorizontally` 合并为一次 decode → crop → mirror → compress 流水线

---

## 路径 3：ToggleGuides -> AI 分析循环

### 完整调用链

```
👆 UI_CLICK: ToggleGuides (main#2) +0ms
  |
  +-- [场景识别循环, 每 1.5s]
  |     +-- START: captureScaledBitmap (main#2) <-- 主线程!
  |     |     \-- previewView.captureScaledBitmap()  ~30-100ms <-- 瓶颈1
  |     \-- START: AiBackend.detectScene (DefaultDispatcher) ~100-300ms
  |
  +-- [云端AI循环, 每 10s]
  |     +-- START: captureScaledBitmap (main#2) ~30-100ms <-- 瓶颈2
  |     \-- withContext(IO): CloudAiService.analyzeScene()
  |           +-- bitmapToBase64() ~50-150ms
  |           \-- HTTP API call ~1000-5000ms
  |
  \-- [构图分析循环, 每 4s]
        +-- START: captureScaledBitmap (main#2) ~30-100ms <-- 瓶颈3
        \-- withContext(Default): AiBackend.analyzeComposition()
              \-- ML Kit face detector ~200-500ms
```

### 瓶颈详解

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 1 | captureScaledBitmap 阻塞主线程 | [CameraScreen.kt:717](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L717) | 每 1.5s 主线程卡 30-100ms，取景器掉帧 |
| 2 | 三个循环各自独立抓取 bitmap | [CameraScreen.kt:716,758,803](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L716) | 浪费 GPU 带宽，三个分析可共享一张 bitmap |
| 3 | bitmapToBase64 全分辨率编码 | [CloudAiService.kt:114](app/src/main/java/com/aicamera/app/backend/ai/CloudAiService.kt#L114) | 发送原始分辨率到云端，应降采样到 512px |

### 优化建议

1. `captureScaledBitmap()` 包装到 `withContext(Dispatchers.Default)` 中
2. 一次抓取缓存 bitmap，三个分析共用（复用已有 isAiProcessing 锁）
3. 云端 AI 在 base64 编码前降采样到 512px

---

## 路径 4：CameraRebind（翻转摄像头 / HDR 切换）

### 完整调用链

```
👆 UI_CLICK: FlipCamera / ToggleHDR (main#2) +0ms
  |
  \-- LaunchedEffect 检测到状态变化
      +-- START: unbindAll (main#2)  耗时: 10-50ms
      +-- START: bindToLifecycle (main#2)  耗时: 100-300ms <-- 瓶颈
      |     \-- CameraX -> Camera HAL 初始化（框架强制主线程）
      \-- START: ConfigCameraParams (main#2)  耗时: 10-20ms

END: 总耗时 120-370ms，期间显示 isCameraSwitching 黑色遮罩
```

### 瓶颈分析

| # | 问题 | 位置 | 耗时 |
|---|------|------|------|
| 1 | bindToLifecycle 阻塞主线程 | [CameraScreen.kt:970](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L970) | 100-300ms |
| 2 | ExtensionsManager HDR 查询 | [CameraScreen.kt:959-962](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L959) | 与 bind 串行 |

### 优化建议

1. `bindToLifecycle` 必须在主线程（CameraX 框架限制），无法优化
2. `ExtensionsManager.isExtensionAvailable` 结果缓存到 `remember{}`，避免每次重绑定查询

---

## 路径 5：CameraInit（冷启动）

### 完整调用链

```
👆 UI_CLICK: CameraInit (main#2) +0ms
  |
  +-- START: GetCameraProvider (main#2)  耗时: 50-500ms <-- 瓶颈1
  |     \-- CameraPreloadManager.getPreloadedCameraProvider()
  |         或 ProcessCameraProvider.getInstance(context).get()
  +-- START: BuildPreview (main#2)  耗时: 5-10ms
  +-- START: BuildImageCapture (main#2)  耗时: 3-5ms
  +-- START: BindToLifecycle (main#2)  耗时: 100-300ms <-- 瓶颈2
  \-- [后台] ExtensionsManager.getInstanceAsync()  不阻塞

END: 总耗时 158-815ms
```

### 优化建议

1. MainActivity 预加载逻辑已存在（[MainActivity.kt:101](app/src/main/java/com/aicamera/app/MainActivity.kt#L101)），确保 SplashScreen 阶段即触发
2. Preview/ImageCapture Builder 可提前在 background 构造

---

## 路径 6：TapToFocus（单击对焦）

### 调用链

```
👆 UI_CLICK: TapToFocus (main#2) +0ms
  +-- START: FocusRingAnim (main#2)  耗时: 300ms (Compose 动画, 不阻塞)
  +-- meteringPointFactory.createPoint() < 1ms
  \-- cameraControl.startFocusAndMetering() < 1ms
```

**结论**：无性能问题。300ms 是有意设计的动画时长，不阻塞线程。

---

## 路径 7：侧边栏按钮（Flash/HDR/Timer/CloudAI）

所有五个侧边栏按钮都是瞬时状态切换（< 1ms），无性能瓶颈。`ToggleHDR` 和 `FlipCamera` 触发的 CameraRebind 延迟见路径 4。

---

## 优先修复清单

### P0 — 立即修复（用户可直接感知的卡顿）

| # | 修复项 | 文件 | 改动要点 |
|---|--------|------|---------|
| 1 | **拍照后处理切到后台线程** | [CameraBackend.kt:135](app/src/main/java/com/aicamera/app/backend/camera/CameraBackend.kt#L135) | `ContextCompat.getMainExecutor` 改为 IO executor |
| 2 | **消除比例切换 200ms 延迟** | [CameraScreen.kt:708](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L708) | `while+delay(200)` 改为 `snapshotFlow{}.collect{}` |
| 3 | **AI bitmap 抓取移出主线程** | [CameraScreen.kt:717](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L717) | `captureScaledBitmap()` 包到 `withContext(Default)` |

### P1 — 应该修复（显著改善体验）

| # | 修复项 | 文件 |
|---|--------|------|
| 4 | 生产环境禁用诊断 HTTP 上报 | [DiagnosticsBackend.kt:32-44](app/src/main/java/com/aicamera/app/backend/diagnostics/DiagnosticsBackend.kt#L32) |
| 5 | 三个 AI 循环共享 bitmap | [CameraScreen.kt:700-837](app/src/main/java/com/aicamera/app/ui/screens/CameraScreen.kt#L700) |
| 6 | 避免 crop+mirror 重复 decodeFile | [CameraBackend.kt:234-310](app/src/main/java/com/aicamera/app/backend/camera/CameraBackend.kt#L234) |

### P2 — 锦上添花

| # | 修复项 |
|---|--------|
| 7 | 云端 AI bitmap 降采样到 512px 再 base64 编码 |
| 8 | 缓存 ExtensionsManager.isExtensionAvailable 查询结果 |
| 9 | Preview/ImageCapture Builder 提前在后台构造 |

---

## 预期日志输出示例

基于埋点代码，运行 `adb logcat -s PerfTracer` 后每个操作会输出类似：

```
PerfTracer: ═══════════════════════════════════════════════════════════
PerfTracer:   TRACE #42  |  总耗时: 358ms  |  事件数: 8
PerfTracer:   触发: AspectRatioChange  @ +0ms
PerfTracer: ───────────────────────────────────────────────────────────
PerfTracer: CLICK  +     0ms  [main#2]  AspectRatioChange  |  ratio=0.5625
PerfTracer: START  +   201ms  [main#2]  computeTargetBounds  |  ratio=0.5625
PerfTracer: END    +   202ms  [main#2]  computeTargetBounds  |  bounds=1080x1920
PerfTracer: START  +   203ms  [main#2]  AnimPhase1_Blur  |  duration=50ms
PerfTracer: END    +   255ms  [main#2]  AnimPhase1_Blur
PerfTracer: START  +   256ms  [main#2]  AnimPhase2_Clear  |  duration=250ms
PerfTracer: END    +   510ms  [main#2]  AnimPhase2_Clear
PerfTracer: START  +   511ms  [main#2]  DiagnosticsReport  |  trigger=ratio_change
PerfTracer: END    +   558ms  [DefaultDispatcher-worker-1#47]  DiagnosticsReport
PerfTracer: ───────────────────────────────────────────────────────────
PerfTracer:   耗时排名 (Top 5):
PerfTracer:     1. AnimPhase2_Clear = 254ms
PerfTracer:     2. AnimPhase1_Blur = 52ms
PerfTracer:     3. DiagnosticsReport = 47ms
PerfTracer:     4. computeTargetBounds = 1ms
PerfTracer: ═══════════════════════════════════════════════════════════
```
