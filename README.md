# CameraX-AI 驱动的智能摄影助手

> **让每一拍都成为佳作**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)

大学生软件创新赛事参赛作品 - 基于 Android 平台的智能摄影辅助应用

---

## 📱 项目简介

CameraX-AI 是一款聚焦"**拍前构图辅助**"核心需求的智能摄影应用，通过本地 AI + 云端 AI 双轨架构，为摄影爱好者提供：

- 🎯 **实时构图辅助** - AI 实时检测人脸位置，提供构图调整建议
- 🏞️ **场景智能识别** - 自动识别人像、风景、美食、夜景等场景
- ✂️ **智能裁剪优化** - 基于物体检测的自动裁剪建议
- 🎨 **AI 色彩增强** - 使用 ONNX 模型一键智能调色
- 📷 **专业相机控制** - 曝光补偿、HDR、变焦等专业参数
- 🔒 **隐私保护** - 核心功能本地处理，照片数据不出设备

---

## 🎯 核心亮点

| 特性 | 说明 | 状态 |
|------|------|------|
| **拍前辅助** | 区别于传统后期修图，从源头提升质量 | ✅ 完成 |
| **双轨 AI 架构** | 本地 ML Kit + 云端阿里云百炼 | ✅ 完成 |
| **边缘 AI 推理** | ONNX Runtime + 自定义模型 | ✅ 完成 |
| **HDR 拍照** | CameraX HDR Extension 支持 | ✅ 完成 |
| **隐私保护** | 核心 AI 功能纯本地处理，离线可用 | ✅ 完成 |

---

## 📊 开发进度

### 总体进度：95%

| 模块 | 进度 | 状态 |
|------|------|------|
| **UI 界面** | 100% | ✅ 完成 (6个界面) |
| **相机功能** | 100% | ✅ 完成 |
| **AI 功能** | 100% | ✅ 完成 |
| **图片处理** | 100% | ✅ 完成 |
| **HDR 功能** | 90% | ✅ 基础功能完成 |

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────┐
│      UI 层 (Jetpack Compose)       │
│  Splash | Camera | Edit | Crop     │
│  ColorAdjust | Settings            │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│    业务逻辑层 (Kotlin)              │
│  CameraBackend | AiBackend          │
│  ColorBackend | CropBackend         │
│  StorageBackend | HdrService        │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      AI 与图像处理层                │
│  ML Kit | ONNX Runtime | CameraX   │
│  OpenCV | Camera2 API              │
└─────────────────────────────────────┘
```

### 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| **语言** | Kotlin | 2.0.21 |
| **UI 框架** | Jetpack Compose | BOM 2024.09.00 |
| **设计系统** | Material 3 | - |
| **相机 API** | CameraX | 1.4.1 |
| **AI 推理** | ONNX Runtime | 1.19.0 |
| **场景识别** | ML Kit Image Labeling | 17.0.9 |
| **人脸检测** | ML Kit Face Detection | 16.1.7 |
| **物体检测** | ML Kit Object Detection | 17.0.2 |
| **云端 AI** | 阿里云百炼 | qwen-vl-plus |
| **图像处理** | OpenCV | 4.9.0 |
| **图片加载** | Coil | 2.4.0 |
| **权限管理** | Accompanist | 0.32.0 |

---

## 📦 功能模块

### 1. 相机功能

- ✅ 相机预览（支持 1:1、4:3、16:9、全屏比例）
- ✅ 拍照保存（支持定时器 3s/10s）
- ✅ 变焦控制（0.5x-10x 预设 + 扇形滑块）
- ✅ 前后摄像头切换
- ✅ 闪光灯控制（自动/开启/关闭）
- ✅ HDR 模式（CameraX HDR Extension）
- ✅ 曝光补偿控制（-12 ~ +12 EV）
- ✅ 专业参数显示（ISO、快门速度）
- ✅ 相册缩略图显示

### 2. AI 功能

| 功能 | 技术实现 | 状态 |
|------|---------|------|
| **场景识别** | ML Kit Image Labeling | ✅ 完成 |
| **人脸检测** | ML Kit Face Detection | ✅ 完成 |
| **构图建议** | 人脸位置分析 + 三分法 | ✅ 完成 |
| **物体检测** | ML Kit Object Detection | ✅ 完成 |
| **智能裁剪** | 物体检测 + 智能边距算法 | ✅ 完成 |
| **AI 调色** | ONNX Runtime 自定义模型 | ✅ 完成 |
| **云端 AI** | 阿里云百炼 qwen-vl-plus | ✅ 完成 |
| **高级构图** | OpenCV 辅助分析 | ✅ 完成 |

### 3. HDR 处理模块

- ✅ Camera2 HDR 捕获（HdrCaptureController）
- ✅ 多帧 Burst 捕获
- ✅ HDR 处理管线（HdrProcessor）
- ✅ OpenGL ES 后处理支持
- ⏳ GLSL 着色器（去马赛克、降噪、锐化、色调映射）

### 4. 图片处理

- ✅ 智能裁剪（AI 识别 + 手动调整）
- ✅ 调色功能（曝光、对比度、饱和度、锐度、色温、高光）
- ✅ AI 一键增强
- ✅ 图片旋转
- ✅ 保存到相册
- ✅ Exif 信息保留

---

## 📂 项目结构

```
ai_camera_new/
├── app/src/main/
│   ├── java/com/aicamera/app/
│   │   ├── MainActivity.kt
│   │   ├── backend/
│   │   │   ├── ai/                    # AI 后端
│   │   │   │   ├── AiBackend.kt       # 场景识别、人脸检测、构图分析
│   │   │   │   ├── CloudAiService.kt  # 阿里云百炼云端 AI
│   │   │   │   └── SecurePrefs.kt     # 加密存储 API Key
│   │   │   ├── camera/                # 相机功能
│   │   │   │   ├── CameraBackend.kt
│   │   │   │   ├── CameraAdvancedControls.kt
│   │   │   │   ├── CameraSession.kt
│   │   │   │   └── QualityConfig.kt
│   │   │   ├── hdr/                   # HDR 处理模块
│   │   │   │   ├── HdrService.kt
│   │   │   │   ├── HdrProcessor.kt
│   │   │   │   ├── capture/
│   │   │   │   │   └── HdrCaptureController.kt
│   │   │   │   ├── gl/
│   │   │   │   │   ├── EGLContextManager.kt
│   │   │   │   │   └── PostPipeline.kt
│   │   │   │   └── model/
│   │   │   │       ├── ImageFrame.kt
│   │   │   │       ├── NoiseModeler.kt
│   │   │   │       └── ProcessingParameters.kt
│   │   │   ├── color/                 # 色彩调整
│   │   │   │   ├── ColorBackend.kt
│   │   │   │   ├── ColorAdjustmentUtils.kt
│   │   │   │   └── OnnxColorModel.kt
│   │   │   ├── crop/                  # 裁剪功能
│   │   │   │   └── CropBackend.kt
│   │   │   ├── opencv/                # OpenCV 构图分析
│   │   │   │   ├── OpenCvHelper.kt
│   │   │   │   └── CompositionEngine.kt
│   │   │   ├── storage/               # 存储管理
│   │   │   │   ├── StorageBackend.kt
│   │   │   │   └── ExifUtils.kt
│   │   │   ├── rotate/                # 旋转功能
│   │   │   │   └── RotateBackend.kt
│   │   │   ├── gallery/               # 相册功能
│   │   │   │   └── GalleryBackend.kt
│   │   │   ├── diagnostics/           # 诊断功能
│   │   │   │   └── DiagnosticsBackend.kt
│   │   │   └── models/                # 数据模型
│   │   │       ├── Models.kt
│   │   │       └── CompositionModels.kt
│   │   └── ui/
│   │       ├── screens/               # 6 个界面
│   │       │   ├── SplashScreen.kt
│   │       │   ├── CameraScreen.kt
│   │       │   ├── EditScreen.kt
│   │       │   ├── CropScreen.kt
│   │       │   ├── ColorAdjustScreen.kt
│   │       │   └── SettingsScreen.kt
│   │       ├── components/            # 组件库
│   │       │   └── CommonComponents.kt
│   │       ├── panels/                # 面板组件
│   │       │   ├── ParamSettingsPanel.kt
│   │       │   └── AspectRatioPanel.kt
│   │       └── theme/                 # 主题系统
│   │           ├── Color.kt
│   │           ├── Theme.kt
│   │           └── Type.kt
│   ├── assets/
│   │   ├── models/                    # ONNX 模型
│   │   │   └── mobilenetv2_color.onnx
│   │   └── shaders/                   # GLSL 着色器
│   │       ├── demosaic/
│   │       ├── denoise/
│   │       ├── sharpening/
│   │       └── tonemap/
│   └── res/
├── gradle/
│   └── libs.versions.toml
├── docs/
└── licenses/
```

---

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 11 或更高版本
- Android SDK API 36
- Gradle 8.5

### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/sinc-star/AI_Camera_app.git
cd AI_Camera_app
```

2. **使用 Android Studio 打开项目**

3. **同步 Gradle 项目**
   - 点击 "Sync Project with Gradle Files"

4. **运行应用**
   - 连接 Android 设备或启动模拟器
   - 点击 "Run" 按钮

---

## 📊 性能指标

| 指标 | 目标值 | 当前状态 |
|------|--------|----------|
| 相机预览帧率 | ≥ 30 FPS | ✅ 已达标 |
| 场景识别延迟 | < 1 秒 | ✅ 已达标 |
| AI 调色推理 | < 200ms | ✅ 已达标 |
| 内存占用 | < 300 MB | ✅ 约 280MB |
| APK 大小 | < 50 MB | ✅ 约 35MB |

---

## 🏆 竞争优势

### 差异化定位

| 维度 | 传统应用 | 本项目 |
|------|----------|--------|
| **介入时机** | 拍摄后修图 | 拍前指导 |
| **AI 架构** | 纯云端 | 本地 + 云端双轨 |
| **隐私保护** | 需上传 | 核心功能本地处理 |
| **网络依赖** | 必须 | 离线可用 |
| **专业控制** | 有限 | 完整参数支持 |

### 核心竞争力

1. **双轨 AI 架构**：本地快速响应 + 云端深度分析
2. **专业相机控制**：满足摄影爱好者进阶需求
3. **隐私保护**：核心 AI 功能纯本地处理
4. **完整工作流**：拍摄 → AI 辅助 → 编辑 → 保存

---

## 🔐 开源协议

本项目采用 **Apache License 2.0** 开源协议

### 许可证兼容性

| 许可证类型 | 数量 | 兼容性 | 说明 |
|-----------|------|--------|------|
| Apache 2.0 | 19 | ✅ 完全兼容 | 允许商业使用、修改和分发 |
| MIT | 1 | ✅ 完全兼容 | 最宽松的开源许可证 |
| EPL 1.0 | 1 | ✅ 兼容 | 需要公开修改内容 |

详见 [LICENSE](LICENSE) 和 [licenses/](licenses/) 目录。

---

## 🙏 致谢

感谢以下开源项目的贡献：

- [AndroidX](https://developer.android.com/jetpack/androidx) - 现代化 Android 开发库
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 声明式 UI 框架
- [CameraX](https://developer.android.com/training/camerax) - 相机 API 封装
- [ML Kit](https://developers.google.com/ml-kit) - 移动端 AI 能力
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) - 跨平台模型推理
- [OpenCV](https://opencv.org/) - 计算机视觉库
- [Coil](https://github.com/coil-kt/coil) - 图片加载库
- [Accompanist](https://github.com/google/accompanist) - Compose 扩展
- [Material Components](https://github.com/material-components/material-components-android) - 设计系统

---

## 📝 更新日志

### v2.1.0 (2026-04-11)
- ✅ 新增 HDR 处理模块基础功能
- ✅ 新增 OpenCV 构图分析支持
- ✅ 新增三种主题风格切换
- ✅ 完善云端 AI 设置界面
- ✅ 优化内存管理和图片处理

### v2.0.0 (2026-04-05)
- ✅ 完成 AI 功能（场景识别、人脸检测、物体检测、AI 调色）
- ✅ 集成阿里云百炼云端 AI
- ✅ 完成相机专业参数控制
- ✅ 完成智能裁剪功能

### v1.0.0 (2026-03-21)
- ✅ 完成所有 UI 界面（6 个）
- ✅ 封装可复用组件库
- ✅ 实现相机预览功能
- ✅ 建立完整导航系统

---

**最后更新**: 2026-04-11  
**版本**: v2.1.0  
**状态**: ✅ 开发完成

---

*CameraX-AI 驱动的智能摄影助手 - 大学生软件创新赛事参赛作品*

*让每一拍都成为佳作*
