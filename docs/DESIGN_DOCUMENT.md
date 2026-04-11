# CameraX-AI 智能摄影助手 - 设计思路

## 大学生软件创新赛事参赛作品

***

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

| 用户群体   | 核心需求      | 痛点               |
| ------ | --------- | ---------------- |
| 摄影初学者  | 学习构图法则    | 缺乏专业知识，不知道什么是好构图 |
| 社交媒体用户 | 快速产出高质量照片 | 缺乏时间和技巧进行后期处理    |
| 旅行爱好者  | 记录美好瞬间    | 光线、场景变化快，来不及调整参数 |
| 美食博主   | 快速拍摄并美化   | 需要快速产出吸引人的照片     |

***

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

| 维度   | 传统应用  | 本项目       |
| ---- | ----- | --------- |
| 介入时机 | 拍摄后修图 | 拍前指导      |
| 处理方式 | 云端 AI | 本地+云端双轨   |
| 用户价值 | 后期补救  | 源头提升      |
| 隐私保护 | 数据上传  | 本地优先，云端可选 |

#### 2.1.2 自然增强美学

**设计原则**：

- 反对过度美颜和滤镜
- 追求自然真实的影像观感
- AI 参数建议控制在合理范围内

**参数限制示例**：

```kotlin
// AI 增强参数限制（ColorAdjustmentUtils.kt）
val exposure = rawExposure.coerceIn(-1.0f, 1.0f)
val contrast = rawContrast.coerceIn(0.5f, 2.0f)
val saturation = rawSaturation.coerceIn(0.5f, 2.0f)
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

- 智能帧率控制（ML Kit 分析间隔）
- 图片压缩优化（预览帧分析）
- 异步处理避免阻塞 UI

***

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
│            Kotlin + Coroutines                             │
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
│  │ Image Labeling  │ 色彩增强模型   │ 边缘检测       │    │
│  │ Face Detection  │ (6维参数输出)  │ 构图分析       │    │
│  │ Object Detection│                │                │    │
│  └─────────────────┴────────────────┴────────────────┘    │
│                                                           │
│  ┌─── 云端大模型 ────────────────────────────────────┐    │
│  │ 阿里云百炼 API  │ qwen-vl-plus 视觉语言模型      │    │
│  │ 场景深度理解    │ 专业摄影建议生成                │    │
│  └──────────────────┴────────────────────────────────┘    │
│                                                           │
│  ┌─── HDR 处理 ─────────────────────────────────────┐    │
│  │ Camera2 API      │ OpenGL ES                    │    │
│  │ HdrCaptureController                            │    │
│  └───────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 模块划分

#### 3.2.1 UI 层设计

**设计原则**：

- 使用 Jetpack Compose 实现声明式 UI
- 遵循 Material Design 3 设计规范
- 支持三种主题风格（专业/科技/清新）

**模块职责**：

| 模块                | 职责          | 关键技术               |
| ----------------- | ----------- | ------------------ |
| SplashScreen      | 启动引导、功能展示   | Compose Animation  |
| CameraScreen      | 相机预览、实时构图指导 | CameraX Preview    |
| EditScreen        | 图片预览、工具选择   | Coil Image Loading |
| CropScreen        | 智能裁剪、手动调整  | Canvas Drawing     |
| ColorAdjustScreen | 调色参数、AI 增强  | GPU Processing     |
| SettingsScreen    | 相机参数、主题切换、云端AI配置 | DataStore          |

#### 3.2.2 业务逻辑层设计

**设计原则**：

- 使用单例 Object 管理后端服务
- 使用 Coroutines 处理异步操作
- 单一职责原则（每个 Backend 负责一个功能域）

**模块职责**：

| 模块             | 职责            | 关键方法                                                   |
| -------------- | ------------- | ------------------------------------------------------ |
| CameraBackend  | 相机控制、参数获取     | `capturePhoto()`, `switchCamera()`, `setFlashMode()`   |
| AiBackend      | AI 推理、结果处理    | `detectScene()`, `analyzeComposition()`                |
| ColorBackend   | 调色处理、ONNX 推理  | `analyzeColorEnhancement()`, `applyColorAdjustments()` |
| CropBackend    | 智能裁剪、主体检测     | `analyzeSmartCrop()`, `cropImage()`                    |
| StorageBackend | 数据存储、缓存管理     | `savePhoto()`, `clearCache()`                           |
| HdrService     | HDR 拍照、多帧融合   | `captureHdr()`, `getProgress()`                        |
| CloudAiService | 云端 AI 调用、摄影建议 | `analyzeWithCloudAi()`, `setApiKey()`                  |

#### 3.2.3 AI 层设计

**设计原则**：

- 采用双轨 AI 架构：本地轻量化模型 + 云端大模型协同
- 本地模型负责实时性要求高的功能
- ONNX Runtime 进行本地模型推理

**模块职责**：

| 模块             | 职责          | 模型/技术                                           |
| -------------- | ----------- | ----------------------------------------------- |
| AiBackend      | 场景识别、构图分析   | ML Kit Image Labeling + Face Detection + OpenCV |
| ColorBackend   | 色彩增强        | ONNX Runtime + 自定义模型                           |
| CropBackend    | 智能裁剪        | ML Kit Object Detection                         |
| CloudAiService | 深度场景理解、专业建议 | 阿里云百炼 qwen-vl-plus 视觉语言模型                       |
| OpenCvHelper   | 构图分析辅助      | OpenCV 4.9.0                                    |

### 3.3 数据流设计

#### 3.3.1 相机预览数据流

```
CameraX Preview
      ↓
┌─────────────────┐
│  Frame Callback │  (30 FPS)
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  AI 分析帧      │  (按需分析，使用协程)
└────────┬────────┘
         │
    ┌────┴────┐
    ↓         ↓
┌───────┐ ┌───────────┐
│场景识别│ │人脸检测   │
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
│  缩放处理        │  (预览使用较小尺寸)
└────────┬────────┘
         │
    ┌────┴────┐
    ↓         ↓
┌───────┐ ┌───────────┐
│ONNX   │ │ML Kit     │
│模型   │ │场景识别   │
└───┬───┘ └─────┬─────┘
    │           │
    └─────┬─────┘
          ↓
┌─────────────────┐
│  调色参数输出    │
│  (6 维参数)     │
│  曝光/对比度/   │
│  饱和度/锐度/   │
│  色温/高光      │
└─────────────────┘
```

***

## 四、功能模块设计

### 4.1 相机模块

#### 4.1.1 核心功能

| 功能      | 描述                     | 技术实现                               |
| ------- | ---------------------- | ---------------------------------- |
| 实时预览    | CameraX Preview 显示相机画面 | `PreviewView`                        |
| 构图辅助线   | 三分法构图辅助线覆盖在预览上         | `Canvas.drawLine()`                |
| 场景识别    | 实时识别拍摄场景类型             | ML Kit Image Labeling              |
| AI 构图建议 | 根据人脸位置给出调整建议           | ML Kit Face Detection + 三分法算法       |
| 拍照      | 拍摄高质量照片                | `ImageCapture.takePicture()`       |

#### 4.1.2 交互设计

**构图辅助线**：

- 位置：屏幕 1/3 和 2/3 处
- 颜色：根据主题变化（专业-琥珀橙、科技-青色、清新-薄荷绿）
- 宽度：2dp

**AI 建议气泡**：

- 位置：屏幕中央偏上
- 显示时机：检测到构图问题时显示
- 内容示例："向上移动一点，将眼睛靠近上方三分线"

#### 4.1.3 状态管理

相机模块采用 Compose 状态管理系统，使用多个独立的状态变量来管理不同的 UI 和相机状态。

```kotlin
// 相机状态（CameraScreen.kt）
var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

// UI 状态
var sceneType by remember { mutableStateOf("通用拍摄") }
var flashEnabled by remember { mutableStateOf(false) }
var hdrEnabled by remember { mutableStateOf(false) }
var zoomLinear by remember { mutableStateOf(0f) }

// AI 建议状态
var compositionTip by remember { mutableStateOf("") }
var showTip by remember { mutableStateOf(false) }
var cloudAiTip by remember { mutableStateOf("") }
```

### 4.2 场景识别模块

#### 4.2.1 识别场景类型

| 场景 | 标签           | 适用建议           |
| -- | ------------ | -------------- |
| 人像 | portrait     | 开启人像模式，建议构图    |
| 风景 | landscape    | 建议使用三分法        |
| 美食 | food         | 建议俯拍，调整色温      |
| 夜景 | night        | 提高 ISO，建议使用闪光灯 |
| 建筑 | architecture | 建议使用网格线        |

#### 4.2.2 技术实现

场景识别模块使用 ML Kit Image Labeling 进行实时场景分析，采用协程方式处理异步操作。

```kotlin
// AiBackend.kt - detectScene 方法
suspend fun detectScene(imageProxy: ImageProxy): SceneDetectionResult {
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.6f)
            .build()
    )

    return try {
        val labels = labeler.process(image).await()
        val detected = labels
            .sortedByDescending { it.confidence }
            .take(6)
            .map { it.text }

        val (sceneType, confidence) = inferScene(labels)
        SceneDetectionResult(...)
    } catch (e: Throwable) {
        // 返回默认结果
    }
}
```

### 4.3 智能裁剪模块

#### 4.3.1 功能设计

| 功能        | 描述               | 实现状态 |
| --------- | ---------------- | --- |
| AI 自动识别主体 | 使用 ML Kit Object Detection 检测图片中的主要物体 | ✅ 完成 |
| 多主体关联检测   | 检测与主主体相关的其他小主体 | ✅ 完成 |
| 智能裁剪建议   | 根据主体大小、位置建议最佳裁剪区域 | ✅ 完成 |
| 动态置信度计算 | 根据检测质量、主体占比计算置信度 | ✅ 完成 |
| 多种裁剪模式   | 支持自动、方形、竖屏、横屏等模式 | ✅ 完成 |
| 智能放弃裁剪   | 当裁剪比例过大时保留原图 | ✅ 完成 |

#### 4.3.2 技术实现

```kotlin
// CropBackend.kt - analyzeSmartCrop 核心逻辑
suspend fun analyzeSmartCrop(imageUri: String, cropMode: CropMode): SmartCropResult {
    // 1. 检测所有物体
    val objects = detector.process(image).await()
    
    // 2. 找到主主体（面积最大）
    val mainSubject = objects.maxBy { it.boundingBox.width() * it.boundingBox.height() }
    
    // 3. 筛选相关主体（距离和面积比判断）
    val relatedSubjects = sortedObjects.drop(1).filter { 
        isRelatedSubject(mainSubject, it, imageWidth, imageHeight) 
    }
    
    // 4. 计算合并外接框
    val combinedRect = calculateBoundingRect(allSubjects, imageWidth, imageHeight)
    
    // 5. 动态边距（大主体使用小边距）
    val paddingRatio = when {
        mainSubjectRatio > LARGE_SUBJECT_THRESHOLD -> LARGE_SUBJECT_PADDING
        hasFace -> DEFAULT_PADDING
        else -> DEFAULT_PADDING
    }
    
    // 6. 判断是否放弃裁剪（裁剪超过40%则放弃）
    if (cropRatio < 0.60f) {
        return SmartCropResult(
            cropRect = CropRect(0f, 0f, 1f, 1f),
            suggestion = "✨ 当前已是最优构图"
        )
    }
    
    // 7. 动态计算置信度
    val confidence = calculateConfidence(mainObject, objects.size, subjectRatio, edgeRatio, relatedSubjects.size)
}
```

### 4.4 AI 调色模块

#### 4.4.1 调色参数

| 参数  | 范围           | 默认值 | 说明        |
| --- | ------------ | --- | --------- |
| 曝光度 | -1.0 ~ +1.0 | 0.0 | 调整整体亮度    |
| 对比度 | 0.5 ~ 2.0 | 1.0 | 调整明暗对比    |
| 饱和度 | 0.5 ~ 2.0 | 1.0 | 调整色彩鲜艳程度  |
| 锐化  | -1.0 ~ +1.0 | 0.0 | 调整边缘清晰度   |
| 色温  | -1.0 ~ +1.0 | 0.0 | 暖色调 ↔ 冷色调 |
| 高光  | 0.0 ~ 1.0 | 0.5 | 调整亮部细节    |

#### 4.4.2 AI 增强功能

```kotlin
// ColorBackend.kt - analyzeColorEnhancement
suspend fun analyzeColorEnhancement(imageUri: String): AIEnhanceResult {
    // 优先使用 ONNX 模型
    try {
        val onnxParams = OnnxColorModel.analyzeImage(bitmap)
        AIEnhanceResult(
            success = true,
            params = onnxParams,
            detectedInfo = "ONNX 模型预测",
            confidence = 0.95f
        )
    } catch (e: Exception) {
        // 回退到 ML Kit 场景识别
        analyzeWithMLKit(bitmap)
    }
}

// ML Kit 备用方案
private suspend fun analyzeWithMLKit(bitmap: Bitmap): AIEnhanceResult {
    val labels = labeler.process(image).await()
    val detectedInfo = top?.text ?: "通用场景"
    
    // 根据场景类型设置启发式参数
    val (exposure, contrast, saturation) = when {
        detectedInfo.contains("person") -> Triple(0.15f, 0.12f, 0.10f)
        detectedInfo.contains("food") -> Triple(0.10f, 0.15f, 0.25f)
        detectedInfo.contains("landscape") -> Triple(0.05f, 0.20f, 0.15f)
        else -> Triple(0.05f, 0.10f, 0.08f)
    }
}
```

***

## 五、技术选型

### 5.1 前端技术栈

| 技术                  | 版本     | 用途     | 选择理由            |
| ------------------- | ------ | ------ | --------------- |
| **Kotlin**          | 2.0.21 | 开发语言   | 现代、简洁、空安全       |
| **Jetpack Compose** | BOM 2024.09.00 | UI 框架  | 声明式、实时预览性能好     |
| **Material 3**      | -      | 设计系统   | 现代化、支持深色主题      |
| **CameraX**         | 1.4.1  | 相机 API | 简化相机开发、自动生命周期管理 |
| **Coil**            | 2.4.0  | 图片加载   | Kotlin 优先、轻量级   |

### 5.2 AI 技术栈

| 技术                          | 版本     | 用途    | 选择理由                       |
| --------------------------- | ------ | ----- | -------------------------- |
| **ML Kit Image Labeling**   | 17.0.9 | 场景识别  | Google 官方、离线可用、性能优化        |
| **ML Kit Face Detection**   | 16.1.7 | 人脸检测  | 高精度、实时性好                   |
| **ML Kit Object Detection** | 17.0.2 | 对象检测  | 智能裁剪支持                     |
| **ONNX Runtime**            | 1.19.0 | 模型推理  | 跨平台、高性能                   |
| **OpenCV**                  | 4.9.0  | 构图分析  | 边缘检测、线检测                   |
| **阿里云百炼 API**               | -      | 云端大模型 | qwen-vl-plus 视觉语言模型       |

### 5.3 架构模式

**采用单例 Backend + Repository 模式**：

```
┌─────────────────────────────────────┐
│            UI Layer                 │
│  (Composable UI + State Management) │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│         Backend Layer               │
│  (Singleton Objects + Coroutines)   │
│  CameraBackend | AiBackend          │
│  ColorBackend | CropBackend         │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│        Third-party SDKs             │
│  ML Kit | ONNX | CameraX | OpenCV   │
└─────────────────────────────────────┘
```

***

## 六、UI/UX 设计

### 6.1 设计规范

#### 6.1.1 颜色系统

**三种主题风格**：

```kotlin
// 专业摄影风格 - 琥珀橙
object ProfessionalColors {
    val Primary = Color(0xFFFFB74D)
    val Background = Color(0xFF121212)
}

// 科技蓝风格 - 霓虹青蓝
object TechColors {
    val Primary = Color(0xFF00E5FF)
    val Background = Color(0xFF0A1628)
}

// 明亮清新风格 - 薄荷绿
object FreshColors {
    val Primary = Color(0xFF4DB6AC)
    val Background = Color(0xFFFFFFFF)
}
```

#### 6.1.2 字体规范

| 用途  | 字体             | 大小   | 权重      |
| --- | -------------- | ---- | ------- |
| 标题  | System Default | 20sp | Medium  |
| 正文  | System Default | 16sp | Regular |
| 标签  | System Default | 14sp | Medium  |
| 参数值 | System Default | 24sp | Bold    |

#### 6.1.3 间距规范

| 元素     | 间距   |
| ------ | ---- |
| 屏幕边缘   | 16dp |
| 组件间距   | 12dp |
| 内部间距   | 8dp  |
| 最小点击区域 | 48dp |

### 6.2 界面布局

#### 6.2.1 相机界面布局

```
┌─────────────────────────────────────┐
│ [场景标签]              [设置按钮]  │  ← 顶部栏
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
│    [ISO]    [快门]    [光圈]       │
│                                     │
│  [闪光]  ┌─────────┐  [相册]       │  ← 底部控制栏
│          │  拍摄   │               │
│          └─────────┘               │
└─────────────────────────────────────┘
```

#### 6.2.2 设置界面布局

设置界面采用顶部面板设计，支持下拉返回手势。

```
┌─────────────────────────────────────┐
│ [返回]              设置           │
├─────────────────────────────────────┤
│  主题风格                           │
│  ┌─────────┐ ┌─────────┐ ┌────────┐│
│  │专业摄影 │ │ 科技蓝  │ │明亮清新││
│  └─────────┘ └─────────┘ └────────┘│
│                                     │
│  云端AI辅助                         │
│  [启用云端AI分析]        [开关]     │
│                                     │
│  缓存管理                           │
│  缓存大小: 1024 KB                  │
│  [      清理缓存      ]             │
└─────────────────────────────────────┘
```

***

## 七、性能优化设计

### 7.1 内存优化

#### 7.1.1 及时释放资源

```kotlin
// 在相机切换和页面退出时释放资源
DisposableEffect(Unit) {
    onDispose {
        imageProxy?.close()
        bitmap?.recycle()
    }
}
```

#### 7.1.2 图片加载优化

```kotlin
// Coil 图片加载配置
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.25)
            .build()
    }
    .build()
```

### 7.2 CPU 优化

#### 7.2.1 异步处理

```kotlin
// AI 推理在后台线程执行
CoroutineScope(Dispatchers.IO).launch {
    val result = analyzeScene(inputImage)
    
    withContext(Dispatchers.Main) {
        updateUI(result)
    }
}
```

### 7.3 模型优化

#### 7.3.1 ONNX 模型配置

```kotlin
// OnnxColorModel.kt
val sessionOptions = OrtSession.SessionOptions().apply {
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
}

val session = env.createSession(modelBytes, sessionOptions)
```

### 7.4 性能测试结果

> 测试环境：Pixel 9a 模拟器 (Android API 36)，测试日期 2026-04-11

| 指标 | 目标值 | 实际值 | 状态 |
|------|--------|--------|------|
| 应用启动时间 | < 5秒 | 3.2秒 | ✅ |
| 应用重新启动时间 | < 5秒 | 3.2秒 | ✅ |
| 比例切换响应时间 | < 300ms | ~300ms（含动画） | ✅ |
| 内存占用 (PSS) | < 300MB | 149MB | ✅ |
| 内存占用 (RSS) | < 500MB | 277MB | ✅ |
| CPU 占用 | < 50% | ~0%（空闲） | ✅ |
| 电池消耗 | < 5% | 0%（模拟器） | ✅ |

**比例切换优化说明**：采用 PhotonCamera 策略，比例切换只调整 UI 遮罩，不重新绑定相机。切换过程包含 300ms 平滑动画（50ms 模糊 + 250ms 清晰过渡），既保证即时响应，又为相机提供缓冲时间。

***

## 八、隐私与安全设计

### 8.1 数据处理原则

| 原则   | 实现方式                        |
| ---- | --------------------------- |
| 本地优先 | 实时性要求高的 AI 功能在设备端完成         |
| 云端可选 | 云端大模型功能需用户主动启用，API Key 加密存储 |
| 数据隔离 | 照片数据存储在应用私有目录               |
| 传输安全 | 云端 API 使用 HTTPS 加密传输        |

### 8.2 权限最小化

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.INTERNET" />
<!-- INTERNET 权限仅用于云端 AI 功能，本地功能无需网络 -->
```

### 8.3 API Key 安全

```kotlin
// SecurePrefs.kt - 使用 EncryptedSharedPreferences 加密存储
object SecurePrefs {
    fun setApiKey(context: Context, apiKey: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    fun getApiKey(context: Context): String? {
        return encryptedPrefs.getString(KEY_API_KEY, null)
    }
}
```

***

## 九、扩展性设计

### 9.1 功能模块化

每个 Backend 都是独立的单例对象，便于测试和替换：

```kotlin
// 可插拔设计示例
object CameraBackend {
    fun capturePhoto(...) { }
    fun switchCamera(...) { }
    fun setFlashMode(...) { }
}

object AiBackend {
    suspend fun detectScene(...) { }
    suspend fun analyzeComposition(...) { }
}
```

### 9.2 主题支持

```kotlin
// Theme.kt - 三种主题切换
enum class ThemeType {
    PROFESSIONAL,  // 专业摄影 - 琥珀橙
    TECH,          // 科技蓝 - 霓虹青蓝
    FRESH          // 明亮清新 - 薄荷绿
}

@Composable
fun getColorScheme(themeType: ThemeType): ColorScheme {
    return when (themeType) {
        ThemeType.PROFESSIONAL -> ProfessionalColorScheme
        ThemeType.TECH -> TechColorScheme
        ThemeType.FRESH -> FreshColorScheme
    }
}
```

***

## 十、总结

### 10.1 设计亮点

1. **"拍前辅助"理念**：差异化定位，从源头提升摄影质量
2. **双轨 AI 架构**：本地轻量化模型保证实时性，云端大模型提供深度理解
3. **三种主题风格**：专业摄影、科技蓝、明亮清新，满足不同用户偏好
4. **智能裁剪算法**：多主体检测、动态边距、智能放弃策略
5. **模块化设计**：单例 Backend 架构，便于维护和扩展

### 10.2 技术创新

1. **双轨 AI 推理**：本地 ML Kit + ONNX + OpenCV，云端 qwen-vl-plus
2. **多模型融合**：场景识别 + 人脸检测 + 物体检测 + 云端深度理解
3. **智能裁剪算法**：基于检测质量、主体占比、多主体关联的综合策略

### 10.3 用户价值

1. **降低摄影门槛**：让普通用户也能拍出专业级照片
2. **提升审美素养**：通过实时指导培养构图意识
3. **灵活隐私保护**：本地功能无需网络，云端功能用户自主选择

***

**文档版本**：v2.1
**最后更新**：2026-04-11
**状态**：已更新，反映项目实际实现

***

*CameraX-AI 驱动的智能摄影助手 - 大学生软件创新赛事参赛作品*
