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
import kotlin.math.sqrt

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

            // 1. 按面积排序，找到主主体（面积最大的）
            val sortedObjects = objects.sortedByDescending {
                it.boundingBox.width() * it.boundingBox.height()
            }
            val mainSubject = sortedObjects.first()
            val mainBox = mainSubject.boundingBox
            val mainArea = mainBox.width() * mainBox.height().toFloat()
            val mainSubjectRatio = mainArea / imageArea

            // 2. 筛选与主主体相关的其他小主体
            val relatedSubjects = sortedObjects.drop(1).filter { obj ->
                isRelatedSubject(mainSubject, obj, imageWidth, imageHeight)
            }

            // 3. 收集所有需要被覆盖的主体
            val allSubjects = listOf(mainSubject) + relatedSubjects
            val combinedRect = calculateBoundingRect(allSubjects, imageWidth, imageHeight)

            // 计算所有检测到的主体类型
            val detectedSubjectTypes = allSubjects.flatMap { inferSubjects(it) }.distinct()
            val hasFace = detectedSubjectTypes.contains(SubjectType.FACE)

            // 4. 根据主主体大小动态调整 padding
            val paddingRatio = when {
                mainSubjectRatio > LARGE_SUBJECT_THRESHOLD -> LARGE_SUBJECT_PADDING
                hasFace -> DEFAULT_PADDING
                else -> DEFAULT_PADDING
            }

            // 5. 计算带 padding 的裁剪框
            val paddedRect = padRect(combinedRect, imageWidth, imageHeight, paddingRatio)

            // 6. 计算外接框占原图比例
            val cropArea = paddedRect.width() * paddedRect.height().toFloat()
            val cropRatio = cropArea / imageArea

            // 7. 判断是否需要裁剪（裁剪比例超过40%则放弃裁剪）
            // 如果剩余面积小于60%，意味着裁剪了超过40%
            if (cropRatio < 0.60f) {
                Log.d("CropBackend", "裁剪后剩余 ${String.format("%.1f%%", cropRatio * 100)}，超过40%需裁剪，放弃裁剪")

                return@withContext SmartCropResult(
                    success = true,
                    cropRect = CropRect(0f, 0f, 1f, 1f),
                    confidence = 0.95f,
                    suggestion = "✨ 当前已是最优构图",
                    detectedSubjects = detectedSubjectTypes,
                    aspectRatio = aspectRatioFor(cropMode)
                )
            }

            // 8. 正常计算裁剪框
            val cropRect = CropRect(
                left = paddedRect.left.toFloat() / imageWidth,
                top = paddedRect.top.toFloat() / imageHeight,
                width = paddedRect.width().toFloat() / imageWidth,
                height = paddedRect.height().toFloat() / imageHeight
            )

            // 9. 动态计算置信度（考虑多主体情况）
            val edgeRatio = 1f - cropRatio
            val confidence = calculateConfidence(
                mainSubject,
                objects.size,
                mainSubjectRatio,
                edgeRatio,
                relatedSubjects.size
            )

            // 10. 生成建议文案
            val suggestion = buildSuggestion(
                hasFace,
                confidence,
                detectedSubjectTypes,
                relatedSubjects.size
            )

            SmartCropResult(
                success = true,
                cropRect = clampCropRect(cropRect),
                confidence = confidence,
                suggestion = suggestion,
                detectedSubjects = detectedSubjectTypes,
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
     * 判断一个小主体是否与主主体"相关"
     * 基于距离和面积比进行判断
     */
    private fun isRelatedSubject(
        main: DetectedObject,
        other: DetectedObject,
        imgWidth: Int,
        imgHeight: Int
    ): Boolean {
        val mainBox = main.boundingBox
        val otherBox = other.boundingBox

        val mainCenterX = (mainBox.left + mainBox.right) / 2f
        val mainCenterY = (mainBox.top + mainBox.bottom) / 2f
        val otherCenterX = (otherBox.left + otherBox.right) / 2f
        val otherCenterY = (otherBox.top + otherBox.bottom) / 2f

        // 距离阈值：基于图像对角线的25%
        val diagonal = sqrt((imgWidth * imgWidth + imgHeight * imgHeight).toFloat())
        val distanceThreshold = diagonal * 0.25f

        val distance = sqrt(
            (mainCenterX - otherCenterX).let { it * it } +
            (mainCenterY - otherCenterY).let { it * it }
        )

        // 小主体面积不能太大（避免纳入其他独立主体）
        val mainArea = mainBox.width() * mainBox.height()
        val otherArea = otherBox.width() * otherBox.height()
        val areaRatio = otherArea.toFloat() / mainArea

        return distance < distanceThreshold && areaRatio < 0.5f
    }

    /**
     * 计算包含多个主体的最小外接矩形
     */
    private fun calculateBoundingRect(
        objects: List<DetectedObject>,
        imgWidth: Int,
        imgHeight: Int
    ): Rect {
        if (objects.isEmpty()) {
            return Rect(0, 0, imgWidth, imgHeight)
        }

        var minLeft = Int.MAX_VALUE
        var minTop = Int.MAX_VALUE
        var maxRight = Int.MIN_VALUE
        var maxBottom = Int.MIN_VALUE

        objects.forEach { obj ->
            val box = obj.boundingBox
            minLeft = min(minLeft, box.left)
            minTop = min(minTop, box.top)
            maxRight = max(maxRight, box.right)
            maxBottom = max(maxBottom, box.bottom)
        }

        return Rect(
            minLeft.coerceIn(0, imgWidth - 1),
            minTop.coerceIn(0, imgHeight - 1),
            maxRight.coerceIn(minLeft + 1, imgWidth),
            maxBottom.coerceIn(minTop + 1, imgHeight)
        )
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
        edgeRatio: Float,
        relatedSubjectCount: Int = 0
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

        // 裁剪比例适中时更可靠（15%-35%的裁剪比例最佳）
        confidence += when {
            edgeRatio in 0.15f..0.35f -> 0.05f
            edgeRatio > 0.5f -> -0.15f // 裁剪过多可能有问题
            else -> 0f
        }

        // 检测到相关小主体增加置信度（构图更丰富）
        confidence += when (relatedSubjectCount) {
            1 -> 0.03f
            2 -> 0.05f
            in 3..5 -> 0.02f
            else -> 0f
        }

        // 过多物体干扰降低置信度
        if (objectCount > 5) {
            confidence -= 0.03f * (objectCount - 5).coerceAtMost(5)
        }

        return confidence.coerceIn(0f, 1f)
    }

    /**
     * 构建裁剪建议文案
     */
    private fun buildSuggestion(
        hasFace: Boolean,
        confidence: Float,
        subjects: List<SubjectType>,
        relatedCount: Int
    ): String {
        return when {
            hasFace && confidence > 0.88f && relatedCount > 0 ->
                "✨ AI 建议：检测到人像及${relatedCount}个相关主体，已优化整体构图"
            hasFace && confidence > 0.85f ->
                "✨ AI 建议：检测到人像，已优化构图"
            hasFace ->
                "✨ AI 建议：检测到人像，建议检查裁剪效果"
            subjects.contains(SubjectType.TEXT) ->
                "✨ AI 建议：检测到文字，请确认内容完整"
            relatedCount > 0 && confidence > 0.8f ->
                "✨ AI 建议：检测到${relatedCount + 1}个主体，已智能优化整体构图"
            confidence > 0.8f ->
                "✨ AI 建议：检测到主体，已智能优化"
            else ->
                "✨ AI 建议：检测到主体，建议手动调整"
        }
    }
}

