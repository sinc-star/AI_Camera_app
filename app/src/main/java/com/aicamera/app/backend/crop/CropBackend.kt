package com.aicamera.app.backend.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.aicamera.app.backend.models.CropMode
import com.aicamera.app.backend.models.CropRect
import com.aicamera.app.backend.models.SmartCropResult
import com.aicamera.app.backend.models.SubjectType
import com.aicamera.app.backend.storage.ExifUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object CropBackend {
    // 裁剪保护阈值配置
    private const val MAX_CROP_RATIO = 0.45f      // 最大允许裁剪比例（超过则放弃裁剪）
    private const val LARGE_SUBJECT_THRESHOLD = 0.75f  // 主体占比阈值（超过视为大主体）
    private const val MIN_CONFIDENCE = 0.6f       // 最低置信度
    private const val DEFAULT_PADDING = 0.15f     // 默认边距比例
    private const val LARGE_SUBJECT_PADDING = 0.08f   // 大主体时减小边距

    suspend fun analyzeSmartCrop(
        imageUri: String,
        cropMode: CropMode = CropMode.AUTO
    ): SmartCropResult = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(imageUri)
            ?: return@withContext defaultResult("无法读取图片", cropMode)

        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        val imageArea = imageWidth * imageHeight.toFloat()

        val image = InputImage.fromBitmap(bitmap, 0)
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        val detector = ObjectDetection.getClient(options)

        try {
            val objects = detector.process(image).await()
            if (objects.isEmpty()) {
                return@withContext defaultResult("未检测到主体，已给出默认裁剪框", cropMode)
            }

            val main = objects.maxBy { it.boundingBox.width() * it.boundingBox.height() }
            val rect = main.boundingBox
            val subjects = inferSubjects(main)

            // 计算主体占比
            val subjectArea = rect.width() * rect.height().toFloat()
            val subjectRatio = subjectArea / imageArea

            // 判断主体类型
            val hasFace = subjects.contains(SubjectType.FACE)

            // 根据主体大小动态调整 padding
            val paddingRatio = when {
                subjectRatio > LARGE_SUBJECT_THRESHOLD -> LARGE_SUBJECT_PADDING
                hasFace -> DEFAULT_PADDING
                else -> DEFAULT_PADDING
            }

            // 计算带 padding 的裁剪框
            val padded = padRect(rect, imageWidth, imageHeight, paddingRatio)

            // 计算当前裁剪框占原图比例
            val cropArea = padded.width() * padded.height().toFloat()
            val cropRatio = cropArea / imageArea

            // 计算需要裁剪掉的部分（边缘区域）
            val edgeArea = imageArea - cropArea
            val edgeRatio = edgeArea / imageArea

            // 检查是否满足裁剪条件
            val shouldSkipCrop = when {
                // 情况1：主体占比过大，裁剪会导致信息丢失
                subjectRatio > LARGE_SUBJECT_THRESHOLD -> {
                    Log.d("CropBackend", "主体占比 ${String.format("%.1f%%", subjectRatio * 100)} 过大，跳过裁剪")
                    true
                }
                // 情况2：需要裁剪的边缘部分超过阈值
                edgeRatio > MAX_CROP_RATIO -> {
                    Log.d("CropBackend", "需裁剪边缘 ${String.format("%.1f%%", edgeRatio * 100)} 超过阈值，跳过裁剪")
                    true
                }
                // 情况3：检测框置信度低（ML Kit 的 trackingId 为 null 通常表示检测不够稳定）
                main.trackingId == null && objects.size > 1 -> {
                    Log.d("CropBackend", "检测置信度较低，存在多个干扰物体")
                    false // 不跳过，但降低置信度
                }
                else -> false
            }

            // 如果应该跳过裁剪，返回基于主体的保守裁剪框或默认裁剪框
            if (shouldSkipCrop) {
                val conservativeRect = calculateConservativeCrop(rect, imageWidth, imageHeight, subjectRatio)
                val (finalRect, confidence) = if (subjectRatio > 0.85f) {
                    // 主体几乎占满画面，使用原图
                    CropRect(0f, 0f, 1f, 1f) to 0.95f
                } else {
                    conservativeRect to 0.75f
                }

                return@withContext SmartCropResult(
                    success = true,
                    cropRect = finalRect,
                    confidence = confidence,
                    suggestion = "✨ AI 建议：主体占比较大，建议保留原图或微调",
                    detectedSubjects = subjects,
                    aspectRatio = aspectRatioFor(cropMode)
                )
            }

            // 正常计算裁剪框
            val cropRect = CropRect(
                left = padded.left.toFloat() / imageWidth,
                top = padded.top.toFloat() / imageHeight,
                width = padded.width().toFloat() / imageWidth,
                height = padded.height().toFloat() / imageHeight
            )

            // 动态计算置信度
            val confidence = calculateConfidence(main, objects.size, subjectRatio, edgeRatio)

            val suggestion = when {
                hasFace && confidence > 0.85f -> "✨ AI 建议：检测到人像，已优化构图"
                hasFace -> "✨ AI 建议：检测到人像，建议检查裁剪效果"
                subjects.contains(SubjectType.TEXT) -> "✨ AI 建议：检测到文字，请确认内容完整"
                confidence > 0.8f -> "✨ AI 建议：检测到主体，已智能优化"
                else -> "✨ AI 建议：检测到主体，建议手动调整"
            }

            SmartCropResult(
                success = true,
                cropRect = clampCropRect(cropRect),
                confidence = confidence,
                suggestion = suggestion,
                detectedSubjects = subjects,
                aspectRatio = aspectRatioFor(cropMode)
            )
        } catch (e: Throwable) {
            Log.e("CropBackend", "analyzeSmartCrop failed", e)
            defaultResult("AI 分析失败，请手动调整", cropMode)
        }
    }

    suspend fun cropImage(
        imageUri: String,
        cropRect: CropRect,
        outputQuality: Int = 95
    ): String = withContext(Dispatchers.Default) {
        val original = BitmapFactory.decodeFile(imageUri)
            ?: throw IllegalArgumentException("无法读取图片: $imageUri")

        val safe = clampCropRect(cropRect)

        val left = (safe.left * original.width).toInt().coerceIn(0, original.width - 1)
        val top = (safe.top * original.height).toInt().coerceIn(0, original.height - 1)
        val width = (safe.width * original.width).toInt().coerceIn(1, original.width - left)
        val height = (safe.height * original.height).toInt().coerceIn(1, original.height - top)

        val cropped = Bitmap.createBitmap(original, left, top, width, height)

        val parent = File(imageUri).parentFile ?: File(imageUri).absoluteFile.parentFile ?: File(".")
        val out = File(parent, "cropped_${System.currentTimeMillis()}.jpg")

        FileOutputStream(out).use { fos ->
            cropped.compress(Bitmap.CompressFormat.JPEG, outputQuality.coerceIn(50, 100), fos)
        }
        ExifUtils.copyExif(imageUri, out.absolutePath)
        out.absolutePath
    }

    private fun defaultResult(message: String, cropMode: CropMode): SmartCropResult {
        return SmartCropResult(
            success = false,
            cropRect = CropRect(0.1f, 0.2f, 0.8f, 0.6f),
            confidence = 0f,
            suggestion = message,
            detectedSubjects = emptyList(),
            aspectRatio = aspectRatioFor(cropMode)
        )
    }

    private fun aspectRatioFor(mode: CropMode): String = when (mode) {
        CropMode.SQUARE -> "1:1"
        CropMode.PORTRAIT -> "3:4"
        CropMode.LANDSCAPE -> "4:3"
        CropMode.AUTO -> "4:3"
    }

    private fun inferSubjects(obj: DetectedObject): List<SubjectType> {
        val labels = obj.labels.map { it.text.lowercase() }
        val subjects = mutableSetOf<SubjectType>()
        labels.forEach { t ->
            when {
                "person" in t || "face" in t -> subjects.add(SubjectType.FACE)
                "text" in t -> subjects.add(SubjectType.TEXT)
                else -> subjects.add(SubjectType.OBJECT)
            }
        }
        if (subjects.isEmpty()) subjects.add(SubjectType.UNKNOWN)
        return subjects.toList()
    }

    private fun padRect(rect: Rect, w: Int, h: Int, paddingRatio: Float): Rect {
        val padX = (rect.width() * paddingRatio).toInt()
        val padY = (rect.height() * paddingRatio).toInt()
        val left = (rect.left - padX).coerceIn(0, w - 1)
        val top = (rect.top - padY).coerceIn(0, h - 1)
        val right = (rect.right + padX).coerceIn(left + 1, w)
        val bottom = (rect.bottom + padY).coerceIn(top + 1, h)
        return Rect(left, top, right, bottom)
    }

    private fun clampCropRect(r: CropRect): CropRect {
        val left = r.left.coerceIn(0f, 1f)
        val top = r.top.coerceIn(0f, 1f)
        val width = r.width.coerceIn(0f, 1f - left)
        val height = r.height.coerceIn(0f, 1f - top)
        val minW = max(width, 0.01f)
        val minH = max(height, 0.01f)
        return CropRect(left, top, minW, minH)
    }

    /**
     * 计算保守的裁剪框，当主体过大或检测不确定时使用
     * 尽量保留更多画面，只做最小程度的裁剪
     */
    private fun calculateConservativeCrop(
        subjectRect: Rect,
        imageWidth: Int,
        imageHeight: Int,
        subjectRatio: Float
    ): CropRect {
        // 主体占比越大，边距越小
        val conservativePadding = when {
            subjectRatio > 0.8f -> 0.02f
            subjectRatio > 0.6f -> 0.05f
            else -> 0.08f
        }

        val padX = (subjectRect.width() * conservativePadding).toInt()
        val padY = (subjectRect.height() * conservativePadding).toInt()

        val left = (subjectRect.left - padX).coerceIn(0, imageWidth - 1)
        val top = (subjectRect.top - padY).coerceIn(0, imageHeight - 1)
        val right = (subjectRect.right + padX).coerceIn(left + 1, imageWidth)
        val bottom = (subjectRect.bottom + padY).coerceIn(top + 1, imageHeight)

        // 确保裁剪框不过小（至少保留主体的 95% 区域）
        val minWidth = (subjectRect.width() * 1.1f).toInt().coerceAtLeast(right - left)
        val minHeight = (subjectRect.height() * 1.1f).toInt().coerceAtLeast(bottom - top)

        val finalRight = min(left + minWidth, imageWidth)
        val finalBottom = min(top + minHeight, imageHeight)
        val finalLeft = max(left, finalRight - minWidth)
        val finalTop = max(top, finalBottom - minHeight)

        return CropRect(
            left = finalLeft.toFloat() / imageWidth,
            top = finalTop.toFloat() / imageHeight,
            width = (finalRight - finalLeft).toFloat() / imageWidth,
            height = (finalBottom - finalTop).toFloat() / imageHeight
        )
    }

    /**
     * 动态计算置信度，基于检测质量和场景复杂度
     */
    private fun calculateConfidence(
        mainObject: DetectedObject,
        objectCount: Int,
        subjectRatio: Float,
        edgeRatio: Float
    ): Float {
        var confidence = 0.75f // 基础置信度

        // 有稳定跟踪 ID 增加置信度
        if (mainObject.trackingId != null) {
            confidence += 0.05f
        }

        // 主体占比适中（30%-70%）时置信度最高
        confidence += when {
            subjectRatio in 0.3f..0.7f -> 0.1f
            subjectRatio in 0.2f..0.8f -> 0.05f
            subjectRatio > 0.85f -> -0.1f // 主体过大可能包含干扰
            else -> 0f
        }

        // 裁剪比例适中时更可靠
        confidence += when {
            edgeRatio in 0.15f..0.35f -> 0.05f
            edgeRatio > 0.5f -> -0.15f // 裁剪过多可能有问题
            else -> 0f
        }

        // 多个物体干扰降低置信度
        if (objectCount > 3) {
            confidence -= 0.05f * (objectCount - 3).coerceAtMost(3)
        }

        return confidence.coerceIn(0f, 1f)
    }
}

