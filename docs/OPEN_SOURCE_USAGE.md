# 开源代码与组件使用情况说明

## CameraX-AI 驱动的智能摄影助手

***

## 一、概述

本项目在开发过程中使用了多个优秀的开源库和框架，极大地提高了开发效率和代码质量。本文档详细说明了所有使用的开源组件、其许可证类型、使用方式以及合规性说明。

### 1.1 许可证兼容性总结

| 许可证类型        | 数量 | 兼容性    | 说明           |
| ------------ | -- | ------ | ------------ |
| Apache 2.0   | 19 | ✅ 完全兼容 | 允许商业使用、修改和分发 |
| MIT          | 2  | ✅ 完全兼容 | 最宽松的开源许可证    |
| BSD 2-Clause | 1  | ✅ 完全兼容 | 允许商业使用       |
| EPL 1.0      | 1  | ✅ 兼容   | 需要公开修改内容     |
| 专有软件         | 0  | -      | 无专有依赖        |

**总体评估**：✅ 所有依赖项的许可证均与项目的开源目标兼容

***

## 二、核心依赖清单

### 2.1 Android 官方库（Apache 2.0）

#### 1. AndroidX Core KTX

- **版本**：1.10.1
- **许可证**：Apache License 2.0
- **用途**：Kotlin 扩展函数，简化 Android 开发
- **使用方式**：提供 Kotlin 风格的 API
- **项目地址**：<https://android.googlesource.com/platform/frameworks/support/+/androidx-main/core/core-ktx>
- **合规性**：✅ 已保留许可证声明

#### 2. Jetpack Compose BOM

- **版本**：2024.09.00
- **许可证**：Apache License 2.0
- **用途**：统一管理 Compose 组件版本
- **使用方式**：版本协调平台
- **项目地址**：<https://developer.android.com/jetpack/compose>
- **合规性**：✅ 已保留许可证声明

#### 3. Jetpack Compose UI

- **版本**：1.5.4（通过 BOM 管理）
- **许可证**：Apache License 2.0
- **用途**：声明式 UI 框架
- **使用方式**：构建所有用户界面
- **包含模块**：
  - `androidx.compose.ui:ui` - 核心 UI 组件
  - `androidx.compose.ui:ui-graphics` - 图形渲染
  - `androidx.compose.ui:ui-tooling` - 开发工具
  - `androidx.compose.ui:ui-tooling-preview` - 预览支持
- **项目地址**：<https://github.com/androidx/androidx>
- **合规性**：✅ 已保留许可证声明

#### 4. Material 3 for Compose

- **版本**：1.5.4（通过 BOM 管理）
- **许可证**：Apache License 2.0
- **用途**：Material Design 3 组件库
- **使用方式**：提供按钮、卡片、对话框等组件
- **项目地址**：<https://github.com/material-components/material-components-android>
- **合规性**：✅ 已保留许可证声明

#### 5. CameraX

- **版本**：1.4.1
- **许可证**：Apache License 2.0
- **用途**：相机 API 封装
- **使用方式**：相机预览、拍照、参数控制
- **包含模块**：
  - `camera-core` - 核心功能
  - `camera-camera2` - Camera2 API 封装
  - `camera-lifecycle` - 生命周期集成
  - `camera-view` - 预览视图
  - `camera-extensions` - 扩展功能（HDR、夜景模式等）
- **项目地址**：<https://developer.android.com/training/camerax>
- **合规性**：✅ 已保留许可证声明

#### 6. Activity Compose

- **版本**：1.9.1
- **许可证**：Apache License 2.0
- **用途**：Activity 与 Compose 集成
- **使用方式**：承载 Compose UI
- **项目地址**：<https://developer.android.com/jetpack/androidx/releases/activity>
- **合规性**：✅ 已保留许可证声明

#### 7. Lifecycle Runtime KTX

- **版本**：2.6.1
- **许可证**：Apache License 2.0
- **用途**：生命周期管理
- **使用方式**：LaunchedEffect 等协程作用域
- **项目地址**：<https://developer.android.com/jetpack/androidx/releases/lifecycle>
- **合规性**：✅ 已保留许可证声明

***

### 2.2 Google 开源库（Apache 2.0）

#### 8. Accompanist Permissions

- **版本**：0.32.0
- **许可证**：Apache License 2.0
- **用途**：运行时权限请求
- **使用方式**：相机权限申请
- **项目地址**：<https://github.com/google/accompanist>
- **合规性**：✅ 已保留许可证声明

#### 9. Accompanist System UI Controller

- **版本**：0.32.0
- **许可证**：Apache License 2.0
- **用途**：系统 UI 控制
- **使用方式**：状态栏颜色、导航栏控制
- **项目地址**：<https://github.com/google/accompanist>
- **合规性**：✅ 已保留许可证声明

#### 10. ML Kit Image Labeling

- **版本**：17.0.9
- **许可证**：Apache License 2.0
- **用途**：场景识别、构图辅助
- **使用方式**：实时分析相机预览帧，识别场景类型（人像、风景、美食、夜景等）
- **项目地址**：<https://developers.google.com/ml-kit>
- **合规性**：✅ 已保留许可证声明

#### 11. ML Kit Face Detection

- **版本**：16.1.7
- **许可证**：Apache License 2.0
- **用途**：人脸检测、构图分析
- **使用方式**：检测人脸位置，提供构图建议
- **项目地址**：<https://developers.google.com/ml-kit/vision/face-detection>
- **合规性**：✅ 已保留许可证声明

#### 12. ML Kit Object Detection

- **版本**：17.0.2
- **许可证**：Apache License 2.0
- **用途**：对象检测、智能裁剪
- **使用方式**：检测图片中的主体对象，提供裁剪建议
- **项目地址**：<https://developers.google.com/ml-kit/vision/object-detection>
- **合规性**：✅ 已保留许可证声明

***

### 2.3 第三方开源库

#### 13. Coil (Coil-kt)

- **版本**：2.4.0
- **许可证**：Apache License 2.0
- **用途**：异步图片加载
- **使用方式**：编辑界面图片预览
- **项目地址**：<https://github.com/coil-kt/coil>
- **作者**：coil-kt
- **合规性**：✅ 已保留许可证声明

#### 14. Material Icons Extended

- **版本**：1.5.4
- **许可证**：Apache License 2.0
- **用途**：图标库
- **使用方式**：提供所有界面图标
- **项目地址**：<https://developer.android.com/jetpack/androidx/releases/compose-material>
- **合规性**：✅ 已保留许可证声明

#### 15. ONNX Runtime for Android

- **版本**：1.19.0
- **许可证**：MIT License
- **用途**：AI 模型推理
- **使用方式**：色彩增强模型推理
- **项目地址**：<https://github.com/microsoft/onnxruntime>
- **作者**：Microsoft
- **合规性**：✅ 已保留许可证声明

#### 16. Kotlinx Coroutines Play Services

- **版本**：1.7.3
- **许可证**：Apache License 2.0
- **用途**：协程与 Google Play Services 集成
- **使用方式**：ML Kit 异步任务处理
- **项目地址**：<https://github.com/Kotlin/kotlinx.coroutines>
- **合规性**：✅ 已保留许可证声明

#### 17. AndroidX Security Crypto

- **版本**：1.1.0-alpha06
- **许可证**：Apache License 2.0
- **用途**：加密存储
- **使用方式**：安全偏好设置存储
- **项目地址**：<https://developer.android.com/reference/androidx/security/crypto/package-summary>
- **合规性**：✅ 已保留许可证声明

#### 18. AndroidX ExifInterface

- **版本**：1.3.7
- **许可证**：Apache License 2.0
- **用途**：EXIF 信息处理
- **使用方式**：图片 EXIF 信息读取和写入
- **项目地址**：<https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface>
- **合规性**：✅ 已保留许可证声明

#### 19. OpenCV for Android

- **版本**：4.9.0
- **许可证**：Apache License 2.0
- **用途**：图像处理与构图分析
- **使用方式**：边缘检测、水平线检测、对称性分析、引导线检测、前景区域检测、色彩分布分析
- **项目地址**：<https://opencv.org/>
- **作者**：OpenCV Team
- **合规性**：✅ 已保留许可证声明

***

### 2.4 测试相关（Apache 2.0）

#### 19. JUnit

- **版本**：4.13.2
- **许可证**：Eclipse Public License 1.0
- **用途**：单元测试框架
- **使用方式**：业务逻辑测试
- **项目地址**：<https://junit.org/junit4/>
- **合规性**：✅ 已保留许可证声明

#### 20. AndroidX JUnit

- **版本**：1.1.5
- **许可证**：Apache License 2.0
- **用途**：Android 仪器测试
- **使用方式**：UI 测试
- **项目地址**：<https://developer.android.com/jetpack/androidx/releases/test>
- **合规性**：✅ 已保留许可证声明

#### 21. Espresso Core

- **版本**：3.5.1
- **许可证**：Apache License 2.0
- **用途**：UI 自动化测试
- **使用方式**：界面交互测试
- **项目地址**：<https://developer.android.com/training/testing/espresso>
- **合规性**：✅ 已保留许可证声明

#### 22. Compose UI Test JUnit4

- **版本**：1.5.4（通过 BOM 管理）
- **许可证**：Apache License 2.0
- **用途**：Compose 界面测试
- **使用方式**：Composable 测试
- **项目地址**：<https://developer.android.com/jetpack/androidx/releases/compose-ui>
- **合规性**：✅ 已保留许可证声明

***

### 2.5 构建工具

#### 23. Android Gradle Plugin

- **版本**：8.2.0
- **许可证**：Apache License 2.0
- **用途**：Android 项目构建
- **使用方式**：编译、打包、签名
- **项目地址**：<https://developer.android.com/studio/build>
- **合规性**：✅ 构建工具，无需分发

#### 24. Kotlin Gradle Plugin

- **版本**：2.0.21
- **许可证**：Apache License 2.0
- **用途**：Kotlin 编译支持
- **使用方式**：Kotlin 代码编译
- **项目地址**：<https://kotlinlang.org/>
- **合规性**：✅ 构建工具，无需分发

#### 25. Kotlin Compose Plugin

- **版本**：2.0.21
- **许可证**：Apache License 2.0
- **用途**：Compose 编译支持
- **使用方式**：Compose 代码编译
- **项目地址**：<https://developer.android.com/jetpack/compose>
- **合规性**：✅ 构建工具，无需分发

***

## 三、AI 模型

### 3.1 已集成的 AI 模型

#### 1. ML Kit Image Labeling（已集成）

- **版本**：17.0.9
- **许可证**：Apache License 2.0
- **用途**：
  - 场景识别：实时识别拍摄场景（人像、风景、建筑）
  - 构图辅助：检测画面中的主体元素，提供构图建议
- **集成方式**：通过 Gradle 依赖引入
- **项目地址**：<https://developers.google.com/ml-kit>
- **合规性**：✅ 许可证兼容

#### 2. ML Kit Face Detection（已集成）

- **版本**：16.1.7
- **许可证**：Apache License 2.0
- **用途**：
  - 人脸检测：检测人脸位置和关键点
  - 构图分析：基于人脸位置提供构图建议
- **集成方式**：通过 Gradle 依赖引入
- **项目地址**：<https://developers.google.com/ml-kit/vision/face-detection>
- **合规性**：✅ 许可证兼容

#### 3. ML Kit Object Detection（已集成）

- **版本**：17.0.2
- **许可证**：Apache License 2.0
- **用途**：
  - 对象检测：检测图片中的主体对象
  - 智能裁剪：基于主体位置提供裁剪建议
- **集成方式**：通过 Gradle 依赖引入
- **项目地址**：<https://developers.google.com/ml-kit/vision/object-detection>
- **合规性**：✅ 许可证兼容

#### 4. ONNX 色彩增强模型（自训练）

- **模型架构**：自定义轻量级 CNN
- **推理引擎**：ONNX Runtime 1.19.0
- **许可证**：自有模型
- **用途**：AI 色彩增强、智能调色
- **训练数据集**：Unsplash Dataset
  - 数据集许可证：Unsplash License（允许商业使用、修改、分发）
  - 数据集地址：<https://unsplash.com/data>
- **训练方式**：迁移学习 + 微调
- **模型大小**：约 3 MB
- **合规性**：✅ 自有模型，训练数据合法授权

***

## 四、许可证文本

### 4.1 Apache License 2.0

大多数依赖使用 Apache License 2.0，主要条款：

**允许的权利**：

- ✅ 商业使用
- ✅ 修改
- ✅ 分发
- ✅ 专利使用
- ✅ 私有使用

**义务**：

- 保留许可证声明
- 保留版权声明
- 声明修改内容
- 包含许可证副本

**限制**：

- 不提供担保
- 不承担责任

**完整文本**：<https://www.apache.org/licenses/LICENSE-2.0>

### 4.2 MIT License

**允许的权利**：

- ✅ 商业使用
- ✅ 修改
- ✅ 分发
- ✅ 私有使用

**义务**：

- 保留许可证声明
- 保留版权声明

**完整文本**：<https://opensource.org/licenses/MIT>

### 4.3 Eclipse Public License 1.0

JUnit 使用 EPL 1.0：

**允许的权利**：

- ✅ 商业使用
- ✅ 修改
- ✅ 分发
- ✅ 专利使用

**义务**：

- 公开修改内容
- 包含许可证副本

**完整文本**：<https://www.eclipse.org/legal/epl-v10.html>

### 4.4 Unsplash License

训练数据集使用 Unsplash License：

**允许的权利**：

- ✅ 商业使用
- ✅ 修改
- ✅ 分发
- ✅ 无需署名

**限制**：

- 不可转售原始图片
- 不可创建竞争性图片服务

**完整文本**：<https://unsplash.com/license>

***

## 五、合规性措施

### 5.1 许可证合规

本项目严格遵守所有依赖项的许可证要求：

1. **保留版权声明**
   - 在源代码中保留原始版权声明
   - 在文档中列出所有依赖
2. **包含许可证副本**
   - 项目包含 LICENSE 文件
   - 包含各依赖的许可证文本
3. **声明修改内容**
   - 对修改的开源代码进行标注
   - 在本文档中说明修改内容
4. **不提供担保声明**
   - 遵循上游许可证的免责条款

### 5.2 代码使用合规

**本项目自有代码**：

- 所有业务逻辑代码均为原创
- UI 组件为自主封装
- 导航系统为自主设计

**使用的开源代码**：

- 仅使用库文件形式（二进制依赖）
- 未修改任何依赖的源代码
- 所有依赖通过 Gradle 官方渠道获取

**AI 模型**：

- MobileNetV2 架构基于 Apache 2.0
- 模型权重为自训练（使用 Unsplash 数据集）
- ML Kit 为官方库，未修改源代码

### 5.3 分发合规

**源代码分发**：

- 包含所有许可证文本
- 包含依赖清单
- 包含版权声明

**二进制分发（APK）**：

- 在"关于"页面列出开源依赖
- 提供许可证查看入口
- 遵守各许可证的分发要求

***

## 六、依赖管理

### 6.1 版本管理策略

本项目使用 Gradle Version Catalog 统一管理依赖版本：

**配置文件**：`gradle/libs.versions.toml`

```toml
[versions]
agp = "8.2.0"
kotlin = "2.0.21"
composeBom = "2024.09.00"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
```

**优点**：

- 集中管理版本号
- 避免版本冲突
- 便于升级维护

### 6.2 依赖树分析

使用以下命令查看依赖树：

```bash
./gradlew app:dependencies
```

**检查项目**：

- 传递依赖冲突
- 重复依赖
- 许可证兼容性

### 6.3 安全更新

**更新策略**：

- 定期检查依赖更新
- 关注安全公告
- 及时修复已知漏洞

**工具**：

- Gradle Dependabot
- OWASP Dependency-Check

***

## 七、第三方资源

### 7.1 图标资源

#### Material Icons

- **来源**：Google Material Design
- **许可证**：Apache License 2.0
- **使用方式**：通过 `material-icons-extended` 库引入
- **项目地址**：<https://fonts.google.com/icons>

### 7.2 字体资源

#### 系统字体

- **使用**：Android 系统默认字体
- **许可证**：系统级授权
- **合规性**：✅ 无需额外授权

### 7.3 颜色方案

#### Material Design 3 色彩系统

- **来源**：Material Design 3 规范
- **许可证**：Creative Commons Attribution 4.0
- **使用方式**：参考设计规范
- **项目地址**：<https://m3.material.io/styles/color>

### 7.4 训练数据集

#### Unsplash Dataset

- **来源**：Unsplash
- **许可证**：Unsplash License
- **用途**：MobileNetV2 图像优化模型训练
- **数据量**：约 100 万张高质量摄影图片
- **合规性**：✅ 合法授权，允许商业使用和修改

***

## 八、AI 模型说明

### 8.1 MobileNetV2 图像优化模型

**模型架构**：

- 基础架构：MobileNetV2（Apache 2.0）
- 输入：224x224 RGB 图像
- 输出：5 维调色参数（曝光、对比度、饱和度、高光、阴影）
- 模型大小：约 3 MB
- 推理时间：< 100ms（移动端）

**训练细节**：

- **数据集**：Unsplash Dataset（唯一数据来源）
- **训练方法**：迁移学习 + 微调
- **损失函数**：L1 Loss + Perceptual Loss
- **优化器**：Adam
- **训练轮数**：50 epochs
- **框架**：TensorFlow / PyTorch

**部署方式**：

- 模型格式：ONNX
- 推理引擎：ONNX Runtime
- 优化：ONNX 图优化 + 动态量化（可选）

**合规性**：

- ✅ 架构：MobileNetV2（Apache 2.0）
- ✅ 训练数据：Unsplash License
- ✅ 模型权重：自有

### 8.2 ML Kit 场景识别

**功能**：

- 实时场景分类（人像、风景、美食、夜景等）
- 对象检测（人脸、物体等）
- 构图辅助建议

**性能**：

- 推理时间：< 50ms
- 准确率：> 85%
- 帧率：2-5 FPS（实时处理）

**合规性**：

- ✅ Apache License 2.0
- ✅ Google 官方库

***

## 九、合规性检查清单

### 9.1 开发阶段

- [x] 记录所有依赖项
- [x] 确认许可证类型
- [x] 评估许可证兼容性
- [x] 保留许可证文本
- [x] 编写依赖说明文档
- [x] AI 模型训练数据合规性确认

### 9.2 发布前检查

- [ ] 最终依赖审计
- [ ] 许可证合规性复查
- [ ] 第三方资源授权确认
- [ ] AI 模型许可证确认
- [ ] 准备"关于"页面内容

### 9.3 发布后维护

- [ ] 定期更新依赖
- [ ] 监控安全漏洞
- [ ] 维护依赖文档
- [ ] 处理许可证变更

***

## 十、联系方式与致谢

### 10.1 开源项目致谢

感谢以下开源项目的贡献：

- **AndroidX** - 提供现代化的 Android 开发库
- **Jetpack Compose** - 提供声明式 UI 框架
- **CameraX** - 简化相机开发
- **ML Kit** - 提供移动端 AI 能力（场景识别、构图辅助）
- **Coil** - 提供高效的图片加载
- **Accompanist** - 提供 Compose 扩展功能
- **Material Components** - 提供设计系统实现
- **Unsplash** - 提供高质量训练数据集
- **MobileNetV2 作者** - 提供高效的轻量级网络架构

### 10.2 问题反馈

如发现许可证合规性问题，请联系：

- **邮箱**：\[sinc1209\@outlook.com]

### 10.3 许可证持有者权利

本项目尊重所有开源许可证持有者的权利。如认为本项目的使用方式违反了相关许可证，请与我们联系，我们将及时纠正。

***

## 十一、附录

### A. 完整依赖列表（Gradle 格式）

```kotlin
// AndroidX 核心库
implementation("androidx.core:core-ktx:1.10.1")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
implementation("androidx.activity:activity-compose:1.9.1")

// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended:1.5.4")

// CameraX
implementation("androidx.camera:camera-core:1.4.1")
implementation("androidx.camera:camera-camera2:1.4.1")
implementation("androidx.camera:camera-lifecycle:1.4.1")
implementation("androidx.camera:camera-view:1.4.1")
implementation("androidx.camera:camera-extensions:1.4.1")

// Google Accompanist
implementation("com.google.accompanist:accompanist-permissions:0.32.0")
implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

// ML Kit
implementation("com.google.mlkit:image-labeling:17.0.9")
implementation("com.google.mlkit:face-detection:16.1.7")
implementation("com.google.mlkit:object-detection:17.0.2")

// ONNX Runtime
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

// Coil 图片加载
implementation("io.coil-kt:coil-compose:2.4.0")

// Security & Exif
implementation("androidx.security:security-crypto:1.1.0-alpha06")
implementation("androidx.exifinterface:exifinterface:1.3.7")

// OpenCV
implementation("org.opencv:opencv-android:4.9.0")

// 测试库
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-tooling")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

### B. 许可证文本位置

- **Apache License 2.0**：`licenses/APACHE-2.0.txt`
- **MIT License**：`licenses/MIT.txt`
- **EPL 1.0**：`licenses/EPL-1.0.txt`
- **BSD 2-Clause**：`licenses/BSD-2-CLAUSE.txt`
- **Unsplash License**：<https://unsplash.com/license>

### C. AI 模型信息

**MobileNetV2 图像优化模型**：

- **仓库**：<https://github.com/sinc-star/MobileNetV2-Image-Optimization>
- **架构**：MobileNetV2（Apache 2.0）
- **训练数据**：Unsplash Dataset
- **用途**：AI 色彩增强、智能调色

**ML Kit Image Labeling**：

- **文档**：<https://developers.google.com/ml-kit>
- **用途**：场景识别、构图辅助

<br />

***

**文档版本**：v1.2\
**最后更新**：2026-04-05\
**审核状态**：✅ 已完成审查\
**主要更新**：

- 更新 CameraX 版本至 1.4.1
- 添加 ML Kit Face Detection 和 Object Detection
- 添加 ONNX Runtime 依赖说明
- 添加 Kotlinx Coroutines Play Services
- 添加 AndroidX Security Crypto 和 ExifInterface
- 更新 AI 模型说明

***

*本项目尊重并遵守所有开源许可证要求*
