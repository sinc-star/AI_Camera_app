package com.aicamera.app.backend.color

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.aicamera.app.backend.models.AIEnhanceResult
import com.aicamera.app.backend.models.ColorAdjustmentParams
import com.aicamera.app.backend.storage.ExifUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 纯 Kotlin 实现的 AI 调色后端
 * 使用 ONNX Runtime 进行模型推理，ColorAdjustmentUtils 应用调色参数
 * 确保与 Python 脚本效果一致
 */
object ColorBackend {

    /**
     * 初始化 ONNX 模型
     * 在 Application.onCreate() 或 MainActivity.onCreate() 中调用
     */
    suspend fun initialize(context: Context): Boolean {
        return OnnxColorModel.initialize(context)
    }

    /**
     * 使用 ONNX 模型分析图像调色参数
     * 如果 ONNX 模型未初始化，回退到 ML Kit 场景识别
     */
    suspend fun analyzeColorEnhancement(imageUri: String): AIEnhanceResult = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(imageUri)
            ?: return@withContext AIEnhanceResult(
                success = false,
                params = ColorAdjustmentParams(0f, 0f, 0f, 0f, 0f, 0f),
                detectedInfo = "无法读取图片",
                confidence = 0f
            )

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
            Log.w("ColorBackend", "ONNX model failed, falling back to ML Kit", e)
            // 回退到 ML Kit 场景识别
            analyzeWithMLKit(bitmap)
        }
    }

    /**
     * 使用 ML Kit 进行场景识别（备用方案）
     */
    private suspend fun analyzeWithMLKit(bitmap: Bitmap): AIEnhanceResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.6f)
                .build()
        )

        return try {
            val labels = labeler.process(image).await()
            val top = labels.maxByOrNull { it.confidence }
            val detectedInfo = top?.text ?: "通用场景"

            // Simple heuristics
            // ML Kit 兜底参数 — 保守值，由 ONNX 管线的 clamp 做二次保护
            val (exposure, contrast, saturation) = when {
                detectedInfo.contains("person") -> Triple(0.05f, 1.05f, 1.03f)
                detectedInfo.contains("food") -> Triple(0.03f, 1.03f, 1.05f)
                detectedInfo.contains("landscape") -> Triple(0.02f, 1.05f, 1.04f)
                else -> Triple(0.02f, 1.02f, 1.02f)
            }

            AIEnhanceResult(
                success = true,
                params = ColorAdjustmentParams(
                    exposure = exposure,
                    contrast = contrast,
                    saturation = saturation,
                    sharpness = 0.05f,
                    temperature = 0f,
                    highlights = 0.05f
                ),
                detectedInfo = detectedInfo,
                confidence = top?.confidence ?: 0.7f
            )
        } catch (e: Throwable) {
            Log.e("ColorBackend", "analyzeWithMLKit failed", e)
            AIEnhanceResult(
                success = false,
                params = ColorAdjustmentParams(0f, 0f, 0f, 0f, 0f, 0f),
                detectedInfo = "AI 分析失败",
                confidence = 0f
            )
        }
    }

    /**
     * 应用调色参数到图像
     * 使用 ColorAdjustmentUtils 确保与 Python 脚本效果一致
     */
    suspend fun applyColorAdjustments(
        imageUri: String,
        params: ColorAdjustmentParams
    ): String = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(imageUri)
            ?: throw IllegalArgumentException("无法读取图片: $imageUri")

        // 使用调色工具类应用参数（与 Python 脚本逻辑一致）
        val adjusted = ColorAdjustmentUtils.applyAdjustments(bitmap, params)

        val parent = File(imageUri).parentFile ?: File(imageUri).absoluteFile.parentFile ?: File(".")
        val out = File(parent, "edited_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { fos ->
            adjusted.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        ExifUtils.copyExif(imageUri, out.absolutePath)
        out.absolutePath
    }

    /**
     * 生成调色预览
     */
    suspend fun generatePreview(
        imageUri: String,
        params: ColorAdjustmentParams
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val bitmap = BitmapFactory.decodeFile(imageUri)
                ?: return@withContext null
            generatePreviewFromBitmap(bitmap, params)
        } catch (e: Exception) {
            Log.e("ColorBackend", "generatePreview failed", e)
            null
        }
    }

    /**
     * 从 Bitmap 生成预览
     */
    suspend fun generatePreviewFromBitmap(
        bitmap: Bitmap,
        params: ColorAdjustmentParams
    ): Bitmap = withContext(Dispatchers.Default) {
        // 使用较小尺寸进行预览，提高性能
        val previewWidth = 800
        val scale = previewWidth.toFloat() / bitmap.width
        val previewHeight = (bitmap.height * scale).toInt()

        val scaledBitmap = if (bitmap.width > previewWidth) {
            Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, true)
        } else {
            bitmap
        }

        // 使用调色工具类生成预览
        ColorAdjustmentUtils.applyAdjustments(scaledBitmap, params)
    }
}
