# CameraX-AI 智能摄影助手 - 设计思路

## 大学生软件创新赛事参赛作品

---

## 一、项目概述

### 1.1 项目背景

随着智能手机摄影功能的日益强大，越来越多的用户开始使用手机进行日常摄影。然而，普通用户在拍摄过程中常常面临以下困扰：

- **构图困难**：不了解摄影构图法则，难以拍出构图优美的照片
- **参数迷茫**：面对不同的拍摄场景，不知道如何调整相机参数
- **后期复杂**：拍摄后的修图过程复杂，需要专业的图像处理知识
- **隐私风险**：现有智能摄影应用大多依赖云端 AI 处理，存在隐私泄露风险

### 1.2 项目定位

本项目定位为一款**面向普通用户的智能摄影辅助应用**，核心价值在于：

> **"让每一拍都成为佳作"**

通过 AI 技术在**拍摄前**提供实时构图指导，帮助用户从源头提升摄影质量，而非传统应用的"拍摄后修图"模式。

### 1.3 目标用户

| 用户群体 | 核心需求 | 痛点 |
|---------|---------|------|
| 摄影初学者 | 学习构图法则 | 缺乏专业知识，不知道什么是好构图 |
| 社交媒体用户 | 快速产出高质量照片 | 缺乏时间和技巧进行后期处理 |
| 旅行爱好者 | 记录美好瞬间 | 光线、场景变化快，来不及调整参数 |
| 美食博主 | 快速拍摄并美化 | 需要快速产出吸引人的照片 |

---

## 二、设计理念

### 2.1 核心理念："拍前辅助"

#### 2.1.1 差异化定位

**传统摄影应用的流程**：
```
拍摄 → 传输到云端 → AI 处理 → 下载到本地 → 分享
       ↑____________________________|
            （依赖网络、隐私风险）
```

**本项目的流程**：
```
拍摄 → 本地 AI 实时分析 → 即时指导/分享
  ↑________|
  （构图辅助、场景识别）
  ↓
（可选）云端大模型深度分析 → 专业摄影建议
```

**核心差异**：
| 维度 | 传统应用 | 本项目 |
|------|---------|--------|
| 介入时机 | 拍摄后修图 | 拍前指导 |
| 处理方式 | 云端 AI | 本地+云端双轨 |
| 用户价值 | 后期补救 | 源头提升 |
| 隐私保护 | 数据上传 | 本地优先，云端可选 |

#### 2.1.2 自然增强美学

**设计原则**：
- 反对过度美颜和滤镜
- 追求自然真实的影像观感
- AI 参数建议控制在合理范围内

**参数限制示例**：
```kotlin
// AI 增强参数限制
val maxExposure = 0.15f    // 曝光最多 ±15%
val maxSaturation = 0.10f  // 饱和度最多 ±10%
val maxContrast = 0.12f    // 对比度最多 ±12%
```

### 2.2 用户体验设计

#### 2.2.1 极简交互

**设计目标**：用户无需学习，即可上手使用

**交互原则**：
1. **零学习成本**：不需要阅读教程，直接打开应用即可使用
2. **实时反馈**：AI 建议以直观的方式呈现（如提示气泡、辅助线）
3. **渐进式引导**：新手用户看到 AI 建议后，自然学习构图法则

#### 2.2.2 即时响应

**性能目标**：
- 相机预览帧率：≥ 30 FPS
- AI 场景识别延迟：< 1 秒
- AI 构图分析延迟：< 2 秒
- 调色处理延迟：< 2 秒

**优化策略**：
- 智能帧率控制（2-5 FPS AI 分析）
- 图片压缩优化（640x480 预览分析）
- Bitmap 复用池（减少内存分配）

---

## 三、系统架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        UI 层                                │
│    Jetpack Compose (Material 3)                            │
│  ┌──────────┬──────────┬──────────┬──────────┐           │
│  │Splash    │Camera    │Edit      │Crop      │           │
│  │Screen    │Screen    │Screen    │Screen    │           │
│  └──────────┴──────────┴──────────┴──────────┘           │
│  ┌──────────┬──────────┐                                 │
│  │Color     │Settings  │                                 │
│  │Screen    │Screen    │                                 │
│  └──────────┴──────────┘                                 │
└────────────────────────┬──────────────────────────────────┘
                         │
┌────────────────────────▼──────────────────────────────────┐
│                    业务逻辑层                             │
│            Kotlin + ViewModel + Coroutines                  │
│  ┌──────────┬──────────┬──────────┬──────────┐             │
│  │Camera    │Ai        │Color     │Crop      │             │
│  │Backend   │Backend   │Backend   │Backend   │             │
│  └──────────┴──────────┴──────────┴──────────┘             │
│  ┌──────────┬──────────┬──────────┐                        │
│  │Storage   │HDR       │Cloud AI  │                        │
│  │Backend   │Service   │Service   │                        │
│  └──────────┴──────────┴──────────┘                        │
└────────────────────────┬──────────────────────────────────┘
                         │
┌────────────────────────▼──────────────────────────────────┐
│              AI 与图像处理层（双轨架构）                    │
│                                                           │
│  ┌─── 本地轻量化模型 ────────────────────────────────┐    │
│  │ ML Kit          │ ONNX Runtime   │ OpenCV 4.9     │    │
│  │ Image Labeling  │ MobileNetV2    │ 边缘检测       │    │
│  │ Face Detection  │ 色彩增强模型   │ 构图分析       │    │
│  │ Object Detection│ (5维参数输出)  │ 对称性分析     │    │
│  └─────────────────┴────────────────┴────────────────┘    │
│                                                           │
│  ┌─── 云端大模型 ────────────────────────────────────┐    │
│  │ 阿里云百炼 API  │ qwen-vl-plus 视觉语言模型      │    │
│  │ 场景深度理解    │ 专业摄影建议生成                │    │
│  └──────────────────┴────────────────────────────────┘    │
│                                                           │
│  ┌─── 图像渲染引擎 ──────────────────────────────────┐    │
│  │ OpenGL ES (GLES20/GLES30)                         │    │
│  │ HDR 后处理管线（去马赛克、降噪、锐化、色调映射）  │    │
│  └───────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 模块划分

#### 3.2.1 UI 层设计

**设计原则**：
- 使用 Jetpack Compose 实现声明式 UI
- 遵循 Material Design 3 设计规范
- 深色主题优先（符合摄影场景）

**模块职责**：

| 模块 | 职责 | 关键技术 |
|------|------|---------|
| SplashScreen | 启动引导、功能展示 | Compose Animation |
| CameraScreen | 相机预览、实时构图指导 | CameraX Preview |
| EditScreen | 图片预览、工具选择 | Coil Image Loading |
| CropScreen | 智能裁剪、AI 识别 | Canvas Drawing |
| ColorAdjustScreen | 调色参数、AI 增强 | GPU Processing |
| SettingsScreen | 相机参数、模型管理 | DataStore |

#### 3.2.2 业务逻辑层设计

**设计原则**：
- 使用 ViewModel 管理 UI 状态
- 使用 Coroutines 处理异步操作
- 单一职责原则（每个 Backend 负责一个功能域）

**模块职责**：

| 模块 | 职责 | 关键方法 |
|------|------|---------|
| CameraBackend | 相机控制、参数获取 | `capturePhoto()`, `switchCamera()`, `setFlashMode()` |
| AiBackend | AI 推理、结果处理 | `detectScene()`, `analyzeComposition()` |
| ColorBackend | 调色处理、ONNX 推理 | `analyzeColorEnhancement()`, `applyColorAdjustments()` |
| CropBackend | 智能裁剪、主体检测 | `analyzeSmartCrop()`, `cropImage()` |
| StorageBackend | 数据存储、缓存管理 | `savePhoto()`, `loadCache()` |
| HdrService | HDR 拍照、多帧融合 | `captureHdr()`, `getProgress()` |
| CloudAiService | 云端 AI 调用、摄影建议 | `analyzeWithCloudAi()`, `getApiKey()` |

#### 3.2.3 AI 层设计

**设计原则**：
- 采用双轨 AI 架构：本地轻量化模型 + 云端大模型协同
- 本地模型负责实时性要求高的功能（场景识别、人脸检测、构图分析、调色）
- 云端大模型负责深度语义理解和专业摄影建议
- ONNX Runtime 进行本地模型推理

**模块职责**：

| 模块 | 职责 | 模型/技术 |
|------|------|------|
| AiBackend | 场景识别、构图分析 | ML Kit Image Labeling + Face Detection + OpenCV |
| ColorBackend | 色彩增强 | ONNX Runtime + MobileNetV2（.onnx 格式，5维参数输出） |
| CropBackend | 智能裁剪 | ML Kit Object Detection |
| CloudAiService | 深度场景理解、专业建议 | 阿里云百炼 qwen-vl-plus 视觉语言模型 |
| OpenCvHelper | 构图分析辅助 | OpenCV 4.9.0（边缘检测、线检测、对称性分析） |

### 3.3 数据流设计

#### 3.3.1 相机预览数据流

```
CameraX Preview
      ↓
┌─────────────────┐
│  Frame Callback │  (30 FPS)
└────────┬────────┘
         │
         ↓ 降采样到 640x480
┌─────────────────┐
│  AI 分析帧      │  (2 FPS, 每 500ms 一帧)
└────────┬────────┘
         │
    ┌────┴────┐
    ↓         ↓
┌───────┐ ┌───────────┐
│场景识别│ │构图分析   │
└───┬───┘ └─────┬─────┘
    │           │
    └─────┬─────┘
          ↓
┌─────────────────┐
│  AI 建议气泡    │
└─────────────────┘
```

#### 3.3.2 图片处理数据流

```
原图加载
    ↓
┌─────────────────┐
│  缩放到 224x224 │  (MobileNetV2 输入尺寸)
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  ONNX Runtime   │
│  MobileNetV2    │
│  (.onnx 推理)   │
└────────┬────────┘
         │
    ┌────┴────┐
    ↓         ↓
┌───────┐ ┌───────────┐
│曝光度  │ │对比度    │
└───┬───┘ └─────┬─────┘
    │           │
    └─────┬─────┘
          ↓
┌─────────────────┐
│  滤镜参数输出    │
│  (5 维参数)     │
│  曝光/对比度/   │
│  饱和度/高光/   │
│  阴影           │
└─────────────────┘
```

---

## 四、功能模块设计

### 4.1 相机模块

#### 4.1.1 核心功能

| 功能 | 描述 | 技术实现 |
|------|------|---------|
| 实时预览 | CameraX Preview 显示相机画面 | `PreviewView.setSurfaceProvider()` |
| 构图辅助线 | 三分法构图辅助线覆盖在预览上 | `Canvas.drawLine()` |
| 场景识别 | 实时识别拍摄场景类型 | ML Kit Image Labeling |
| AI 构图建议 | 根据主体位置给出调整建议 | ML Kit Face Detection + 自研算法 |
| 拍照 | 拍摄高质量照片 | `ImageCapture.takePicture()` |

#### 4.1.2 交互设计

**构图辅助线**：
- 位置：屏幕 1/3 和 2/3 处
- 颜色：半透明绿色（`PrimaryGreen.copy(alpha = 0.5f)`）
- 宽度：2dp

**AI 建议气泡**：
- 位置：屏幕中央
- 显示时机：检测到构图问题时显示
- 自动消失：3 秒后自动隐藏
- 动画：淡入淡出效果

#### 4.1.3 状态管理

```kotlin
data class CameraUiState(
    val isPreviewReady: Boolean = false,
    val sceneType: String = "人像拍摄",
    val compositionScore: Float = 0.0f,
    val compositionSuggestions: List<String> = emptyList(),
    val cameraParams: CameraParams = CameraParams(),
    val flashEnabled: Boolean = false,
    val showGuides: Boolean = true
)
```

### 4.2 场景识别模块

#### 4.2.1 识别场景类型

| 场景 | 标签 | 适用建议 |
|------|------|---------|
| 人像 | portrait | 开启人像模式，建议构图 |
| 风景 | landscape | 建议使用三分法 |
| 美食 | food | 建议俯拍，调整色温 |
| 夜景 | night | 提高 ISO，建议使用闪光灯 |
| 建筑 | architecture | 建议使用网格线 |

#### 4.2.2 技术实现

```kotlin
// ML Kit Image Labeling 集成
val imageLabeler = ImageLabeling.getClient(
    ImageLabelerOptions.Builder()
        .setMaxResultCount(5)
        .build()
)

// 分析相机帧
fun analyzeScene(inputImage: InputImage) {
    imageLabeler.process(inputImage)
        .addOnSuccessListener { labels ->
            val topLabel = labels.firstOrNull()
            updateSceneType(topLabel?.text ?: "Unknown")
        }
}
```

### 4.3 智能裁剪模块

#### 4.3.1 功能设计

| 功能 | 描述 | 优先级 |
|------|------|--------|
| AI 自动识别主体 | 检测图片中的主要物体 | P0 |
| 建议最佳裁剪框 | 根据美学原则建议裁剪区域 | P0 |
| 手动调整裁剪框 | 用户可自由调整裁剪区域 | P0 |
| 多种宽高比 | 支持自由、方形、16:9 等比例 | P1 |
| 裁剪执行 | 应用裁剪并保存 | P0 |

#### 4.3.2 交互设计

**裁剪框**：
- 默认样式：绿色边框，2dp 宽度
- AI 处理中：金色边框，带动画效果
- 四角拖拽手柄：30dp 方形

**辅助线**：
- 九宫格辅助线（1/3、2/3 处）
- 颜色：与边框同色，50% 透明度

### 4.4 AI 调色模块

#### 4.4.1 调色参数

| 参数 | 范围 | 默认值 | 说明 |
|------|------|--------|------|
| 曝光度 | -1.0 ~ +1.0 | 0.0 | 调整整体亮度 |
| 对比度 | -1.0 ~ +1.0 | 0.0 | 调整明暗对比 |
| 饱和度 | -1.0 ~ +1.0 | 0.0 | 调整色彩鲜艳程度 |
| 锐化 | -1.0 ~ +1.0 | 0.0 | 调整边缘清晰度 |
| 色温 | -1.0 ~ +1.0 | 0.0 | 暖色调 ↔ 冷色调 |
| 高光 | -1.0 ~ +1.0 | 0.0 | 调整亮部细节 |

#### 4.4.2 AI 增强功能

```kotlin
// MobileNetV2 图像优化模型（ONNX Runtime 推理）
data class ColorEnhancementResult(
    val exposure: Float,
    val contrast: Float,
    val saturation: Float,
    val highlights: Float,
    val shadow: Float,
    val confidence: Float
)

// 应用 AI 增强
fun applyAIEnhancement(imageUri: String) {
    val inputImage = loadAndResize(imageUri, 224, 224)
    val result = onnxSession.run(inputImage)

    // 应用参数限制
    val clampedResult = clampEnhancement(result)

    // 应用到图片
    applyFilters(clampedResult)
}
```

---

## 五、技术选型

### 5.1 前端技术栈

| 技术 | 版本 | 用途 | 选择理由 |
|------|------|------|---------|
| **Kotlin** | 2.0.21 | 开发语言 | 现代、简洁、空安全 |
| **Jetpack Compose** | 1.5.4 | UI 框架 | 声明式、实时预览性能好 |
| **Material 3** | - | 设计系统 | 现代化、支持深色主题 |
| **CameraX** | 1.4.1 | 相机 API | 简化相机开发、自动生命周期管理 |
| **Coil** | 2.4.0 | 图片加载 | Kotlin 优先、轻量级 |

### 5.2 AI 技术栈

| 技术 | 版本 | 用途 | 选择理由 |
|------|------|------|---------|
| **ML Kit Image Labeling** | 17.0.9 | 场景识别 | Google 官方、离线可用、性能优化 |
| **ML Kit Face Detection** | 16.1.7 | 人脸检测 | 高精度、实时性好 |
| **ML Kit Object Detection** | 17.0.2 | 对象检测 | 智能裁剪支持 |
| **ONNX Runtime** | 1.19.0 | 模型推理 | 跨平台、高性能、支持 MobileNetV2 |
| **OpenCV** | 4.9.0 | 构图分析 | 边缘检测、线检测、对称性分析 |
| **阿里云百炼 API** | - | 云端大模型 | qwen-vl-plus 视觉语言模型，深度场景理解 |

### 5.3 架构模式

**采用 MVVM + Clean Architecture**：

```
┌─────────────────────────────────────┐
│            Presentation             │
│  (Composable UI + ViewModel)        │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│             Domain                   │
│  (Use Cases + Repository Interface) │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│             Data                     │
│  (Repository Implementation)         │
└─────────────────────────────────────┘
```

---

## 六、UI/UX 设计

### 6.1 设计规范

#### 6.1.1 颜色系统

**主色调**：
```kotlin
// 主绿色 - 代表 AI、智能
val PrimaryGreen = Color(0xFF00C853)

// 深色背景 - 突出摄影内容
val DarkBackground = Color(0xFF121212)

// 文字颜色
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFFB0B0B0)
```

#### 6.1.2 字体规范

| 用途 | 字体 | 大小 | 权重 |
|------|------|------|------|
| 标题 | System Default | 20sp | Medium |
| 正文 | System Default | 16sp | Regular |
| 标签 | System Default | 14sp | Medium |
| 参数值 | System Default | 24sp | Bold |

#### 6.1.3 间距规范

| 元素 | 间距 |
|------|------|
| 屏幕边缘 | 16dp |
| 组件间距 | 12dp |
| 内部间距 | 8dp |
| 最小点击区域 | 48dp |

### 6.2 界面布局

#### 6.2.1 相机界面布局

```
┌─────────────────────────────────────┐
│ [场景标签]              [设置按钮]  │  ← 顶部栏 (48dp)
│                                     │
│                                     │
│         ┌─────────────────┐         │
│         │   AI 建议气泡   │         │  ← 居中显示
│         └─────────────────┘         │
│                                     │
│      ════════════════════          │  ← 构图辅助线
│      ║                 ║          │
│      ════════════════════          │
│                                     │
│  [辅助线]              [HDR]        │  ← 右侧工具栏
│  [翻转]                [定时]        │
│                                     │
├─────────────────────────────────────┤
│         [相机参数: ISO100]          │
│    [ISO]    [快门]    [光圈]       │
│                                     │
│  [闪光]  ┌─────────┐  [相册]       │  ← 底部控制栏
│          │  拍摄   │               │
│          └─────────┘               │
└─────────────────────────────────────┘
```

#### 6.2.2 调色界面布局

```
┌─────────────────────────────────────┐
│ [返回]              [调色]  [确认]  │
├─────────────────────────────────────┤
│                                     │
│                                     │
│          ┌───────────────┐          │
│          │               │          │
│          │   图片预览    │          │
│          │   (40% 高度)  │          │
│          │               │          │
│          └───────────────┘          │
│                                     │
├─────────────────────────────────────┤
│  ┌─────────────────────────────┐    │
│  │  AI 增强推荐                 │    │  ← AI 增强卡片
│  │  人物肖像 | 曝光+0.15        │    │
│  │              [应用]         │    │
│  └─────────────────────────────┘    │
│                                     │
│  🌞 曝光度    ──────●──────  +0.15  │  ← 参数滑块
│  对比度      ────●────────  0.00    │
│  饱和度      ──────●──────  +0.10  │
│  ...                                │
└─────────────────────────────────────┘
```

---

## 七、性能优化设计

### 7.1 内存优化

#### 7.1.1 Bitmap 复用池

```kotlin
class BitmapPool {
    private val pool = mutableMapOf<String, Bitmap>()

    fun acquire(width: Int, height: Int): Bitmap {
        val key = "${width}x${height}"
        return pool.getOrPut(key) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }

    fun release(key: String) {
        pool.remove(key)?.recycle()
    }
}
```

#### 7.1.2 及时释放资源

```kotlin
// 在 Compose 中使用 DisposableEffect
DisposableEffect(cameraProvider) {
    onDispose {
        cameraProvider.unbindAll()
        bitmapPool.clear()
    }
}
```

### 7.2 CPU 优化

#### 7.2.1 智能帧率控制

```kotlin
class AnalysisController {
    private val minInterval = 500L  // 最小分析间隔 500ms

    fun shouldAnalyze(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime >= minInterval) {
            lastAnalysisTime = currentTime
            return true
        }
        return false
    }
}
```

#### 7.2.2 异步处理

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    // AI 推理在后台线程执行
    val result = analyzeScene(inputImage)

    withContext(Dispatchers.Main) {
        // UI 更新在主线程执行
        updateUI(result)
    }
}
```

### 7.3 模型优化

#### 7.3.1 模型量化

```python
# 使用 TensorFlow Lite 量化模型
converter = tf.lite.TFLiteConverter.from_saved_model(model_dir)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.representative_dataset = representative_data
quantized_model = converter.convert()
```

#### 7.3.2 输入尺寸优化

```kotlin
// 预览帧降采样
val analysisBitmap = Bitmap.createScaledBitmap(
    previewFrame,
    640,   // 分析尺寸
    480,
    true
)
```

---

## 八、隐私与安全设计

### 8.1 数据处理原则

| 原则 | 实现方式 |
|------|---------|
| 本地优先 | 实时性要求高的 AI 功能在设备端完成 |
| 云端可选 | 云端大模型功能需用户主动启用，API Key 加密存储 |
| 数据隔离 | 照片数据存储在应用私有目录 |
| 即时删除 | 临时文件处理后立即删除 |
| 传输安全 | 云端 API 使用 HTTPS 加密传输 |

### 8.2 权限最小化

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.INTERNET" />
<!-- INTERNET 权限仅用于云端 AI 功能，本地功能无需网络 -->
```

### 8.3 模型安全

- 模型文件存储在应用私有目录
- 模型完整性校验（SHA-256）
- 防逆向工程保护

---

## 九、扩展性设计

### 9.1 功能模块化

```kotlin
// 相机模块接口
interface ICameraModule {
    fun bind(cameraProvider: CameraProvider)
    fun takePhoto(callback: (Uri) -> Unit)
    fun setFlashMode(mode: Int)
    fun switchCamera()
}

// AI 模块接口
interface IAIAnalyzer {
    fun analyzeScene(image: InputImage): SceneResult
    fun analyzeComposition(image: InputImage): CompositionResult
}

// 可插拔设计
class CameraManager(
    private val cameraModule: ICameraModule,
    private val aiAnalyzer: IAIAnalyzer
)
```

### 9.2 主题支持

```kotlin
// 浅色/深色主题切换
@Composable
fun AISmartCameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### 9.3 多语言支持

```xml
<!-- res/values/strings.xml -->
<string name="scene_portrait">人像拍摄</string>
<string name="scene_landscape">风景拍摄</string>
<string name="scene_food">美食拍摄</string>
```

---

## 十、总结

### 10.1 设计亮点

1. **"拍前辅助"理念**：差异化定位，从源头提升摄影质量
2. **双轨 AI 架构**：本地轻量化模型保证实时性，云端大模型提供深度理解
3. **极简交互设计**：零学习成本，即开即用
4. **性能优化**：智能帧率控制，流畅的用户体验
5. **自然增强美学**：追求真实，避免过度处理

### 10.2 技术创新

1. **双轨 AI 推理**：本地 ONNX Runtime + ML Kit + OpenCV，云端 qwen-vl-plus 大模型
2. **多模型融合**：场景识别 + 构图分析 + 色彩增强 + 云端深度理解
3. **性能优化策略**：Bitmap 复用、帧率控制、OpenGL ES 渲染管线

### 10.3 用户价值

1. **降低摄影门槛**：让普通用户也能拍出专业级照片
2. **提升审美素养**：通过实时指导培养构图意识
3. **灵活隐私保护**：本地功能无需网络，云端功能用户自主选择

---

**文档版本**：v1.1
**最后更新**：2026-04-05
**状态**：已更新，反映项目实际实现

---

*CameraX-AI 驱动的智能摄影助手 - 大学生软件创新赛事参赛作品*
