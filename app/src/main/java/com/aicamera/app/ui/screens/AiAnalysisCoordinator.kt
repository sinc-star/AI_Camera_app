package com.aicamera.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import com.aicamera.app.backend.ai.AiBackend
import com.aicamera.app.backend.ai.CloudAiService
import com.aicamera.app.backend.ai.CameraSettingsInfo
import com.aicamera.app.backend.camera.CameraBackend
import com.aicamera.app.backend.diagnostics.PerformanceTracer
import com.aicamera.app.backend.models.SceneType
import com.aicamera.app.ui.components.TipSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * AI分析协调器 — 管理共享bitmap生产者和三个AI分析消费者
 *
 * 四条协程共享同一个bitmap缓存：
 * 1. 生产者：每1.5s从PreviewView抓取一帧
 * 2. 场景识别：消费bitmap进行场景检测
 * 3. 云端AI：低频（10s）调用云端大模型分析
 * 4. 本地构图：每4s进行本地构图分析，云端优先
 */
@Composable
fun AiAnalysisCoordinator(
    showGuides: Boolean,
    cloudAiEnabled: Boolean,
    previewView: PreviewView,
    context: Context,
    iso: String,
    shutter: String,
    sceneTypeState: MutableState<String>,
    detectedObjectsState: MutableState<List<String>>,
    currentTipState: MutableState<String>,
    showTipState: MutableState<Boolean>,
    currentTipSourceState: MutableState<TipSource>,
    cloudAiTipState: MutableState<String>,
    cloudAiTipPendingState: MutableState<Boolean>,
    compositionTipState: MutableState<String>
) {
    // 内部同步状态 — 只在此协调器内部使用
    var isAiProcessing by remember { mutableStateOf(false) }
    var cachedAnalysisBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 共享bitmap生产者 — 每1.5s在后台线程抓取一帧
    LaunchedEffect(showGuides) {
        if (!showGuides) {
            if (!isAiProcessing) {
                cachedAnalysisBitmap?.recycle()
                cachedAnalysisBitmap = null
            }
            return@LaunchedEffect
        }
        var isFirstCapture = true
        while (isActive) {
            if (!isFirstCapture) {
                delay(1500)
            }
            isFirstCapture = false
            if (isAiProcessing) continue
            val oldBitmap = cachedAnalysisBitmap
            val newBitmap = previewView.captureScaledBitmap()
            if (newBitmap != null) {
                cachedAnalysisBitmap = newBitmap
                oldBitmap?.recycle()
            }
        }
    }

    // 场景识别
    LaunchedEffect(showGuides) {
        if (!showGuides) return@LaunchedEffect
        var warmUpRetries = 3
        while (isActive) {
            if (warmUpRetries <= 0) {
                delay(1500)
            }

            if (!isAiProcessing) {
                val bitmap = cachedAnalysisBitmap
                if (bitmap != null) {
                    warmUpRetries = 0
                    isAiProcessing = true
                    try {
                        val e2 = PerformanceTracer.traceStart("AiBackend.detectScene", 0)
                        val result = withContext(Dispatchers.Default) { AiBackend.detectScene(bitmap) }
                        PerformanceTracer.traceEnd(e2, "scene=${result.sceneType}")
                        withContext(Dispatchers.Main) {
                            sceneTypeState.value = when (result.sceneType) {
                                SceneType.PORTRAIT -> "人像拍摄"
                                SceneType.LANDSCAPE -> "风景拍摄"
                                SceneType.FOOD -> "美食拍摄"
                                SceneType.NIGHT -> "夜景拍摄"
                                SceneType.ARCHITECTURE -> "建筑拍摄"
                                SceneType.AUTO -> "通用拍摄"
                            }
                            detectedObjectsState.value = result.detectedObjects
                        }
                    } finally {
                        isAiProcessing = false
                    }
                } else if (warmUpRetries > 0) {
                    warmUpRetries--
                    delay(200)
                }
            } else if (warmUpRetries > 0) {
                warmUpRetries--
                delay(200)
            }
        }
    }

    // 云端AI分析（低频调用）
    LaunchedEffect(showGuides, cloudAiEnabled) {
        if (!showGuides || !cloudAiEnabled) return@LaunchedEffect
        while (isActive) {
            if (!CloudAiService.hasApiKey(context)) {
                Log.d("AiCoordinator", "[AI建议] API Key未配置，跳过云端分析")
                delay(5000)
                continue
            }
            delay(10000)
            if (isAiProcessing) continue
            val bitmap = cachedAnalysisBitmap ?: continue
            isAiProcessing = true
            try {
                val settings = CameraSettingsInfo(
                    iso = if (iso != "Auto") iso.toIntOrNull() else null,
                    shutterSpeed = shutter,
                    ev = CameraBackend.ManualSettings.evIndex
                )

                withContext(Dispatchers.Main) {
                    cloudAiTipPendingState.value = true
                }

                val result = withContext(Dispatchers.IO) {
                    CloudAiService.analyzeScene(context, bitmap, detectedObjectsState.value, settings)
                }

                withContext(Dispatchers.Main) {
                    cloudAiTipPendingState.value = false

                    if (result.success && result.suggestions.isNotEmpty()) {
                        cloudAiTipState.value = result.suggestions.first()
                        currentTipState.value = cloudAiTipState.value
                        currentTipSourceState.value = TipSource.CLOUD
                        showTipState.value = true
                        Log.i("AiCoordinator", "[AI建议] 显示云端建议: ${currentTipState.value}")
                    } else {
                        Log.w("AiCoordinator", "[AI建议] 云端模型调用失败: ${result.errorMessage}，将使用本地模型兜底")
                    }
                }

                delay(4000)

                withContext(Dispatchers.Main) {
                    if (currentTipSourceState.value == TipSource.CLOUD &&
                        currentTipState.value == cloudAiTipState.value) {
                        showTipState.value = false
                        currentTipSourceState.value = TipSource.NONE
                    }
                }
            } finally {
                isAiProcessing = false
            }
        }
    }

    // 构图分析（本地AI兜底）
    LaunchedEffect(showGuides) {
        if (!showGuides) return@LaunchedEffect
        var warmUpRetries = 3
        while (isActive) {
            if (warmUpRetries <= 0) {
                delay(4000)
            }

            if (cloudAiTipPendingState.value || currentTipSourceState.value == TipSource.CLOUD) {
                continue
            }

            if (isAiProcessing) {
                delay(500)
                continue
            }

            val bitmap = cachedAnalysisBitmap
            if (bitmap == null) {
                if (warmUpRetries > 0) {
                    warmUpRetries--
                    delay(300)
                }
                continue
            }
            warmUpRetries = 0
            isAiProcessing = true
            try {
                val st = when (sceneTypeState.value) {
                    "人像拍摄" -> SceneType.PORTRAIT
                    "风景拍摄" -> SceneType.LANDSCAPE
                    "美食拍摄" -> SceneType.FOOD
                    "夜景拍摄" -> SceneType.NIGHT
                    "建筑拍摄" -> SceneType.ARCHITECTURE
                    else -> SceneType.AUTO
                }
                val result = withContext(Dispatchers.Default) {
                    AiBackend.analyzeComposition(bitmap, st)
                }
                val suggestion = result.suggestions.firstOrNull()

                withContext(Dispatchers.Main) {
                    if (result.success && suggestion != null && suggestion.message.isNotBlank()) {
                        compositionTipState.value = suggestion.message
                        if (currentTipSourceState.value != TipSource.CLOUD) {
                            currentTipState.value = compositionTipState.value
                            currentTipSourceState.value = TipSource.LOCAL
                            showTipState.value = true
                            Log.i("AiCoordinator", "[AI建议] 显示本地建议: ${currentTipState.value}")
                        }
                    }
                }

                delay(3000)

                withContext(Dispatchers.Main) {
                    if (currentTipSourceState.value == TipSource.LOCAL &&
                        currentTipState.value == compositionTipState.value) {
                        showTipState.value = false
                        currentTipSourceState.value = TipSource.NONE
                    }
                }
            } finally {
                isAiProcessing = false
            }
        }
    }
}
