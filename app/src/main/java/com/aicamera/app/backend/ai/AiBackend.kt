package com.aicamera.app.backend.ai

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.aicamera.app.backend.models.CameraSettings
import com.aicamera.app.backend.models.CompositionAnalysisResult
import com.aicamera.app.backend.models.CompositionSuggestion
import com.aicamera.app.backend.models.DetectedFace
import com.aicamera.app.backend.models.SceneDetectionResult
import com.aicamera.app.backend.models.SceneType
import com.aicamera.app.backend.models.SuggestionPriority
import com.aicamera.app.backend.models.SuggestionType
import com.aicamera.app.backend.opencv.CompositionEngine
import com.aicamera.app.backend.opencv.OpenCvHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AiBackend {
    /**
     * Scene detection using ML Kit image labeling.
     * NOTE: must call [imageProxy.close] by the caller if this is used inside an analyzer.
     */
    suspend fun detectScene(imageProxy: ImageProxy): SceneDetectionResult {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            return SceneDetectionResult(
                sceneType = SceneType.AUTO,
                confidence = 0f,
                detectedObjects = emptyList(),
                recommendedSettings = CameraSettings(null, null, null)
            )
        }

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
            SceneDetectionResult(
                sceneType = sceneType,
                confidence = confidence,
                detectedObjects = detected,
                recommendedSettings = recommendedSettings(sceneType)
            )
        } catch (e: Throwable) {
            Log.e("AiBackend", "detectScene failed", e)
            SceneDetectionResult(
                sceneType = SceneType.AUTO,
                confidence = 0f,
                detectedObjects = emptyList(),
                recommendedSettings = CameraSettings(null, null, null)
            )
        }
    }

    /**
     * Scene detection from a [Bitmap] (useful for PreviewView.bitmap).
     */
    suspend fun detectScene(bitmap: Bitmap): SceneDetectionResult {
        val image = InputImage.fromBitmap(bitmap, 0)
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
            SceneDetectionResult(
                sceneType = sceneType,
                confidence = confidence,
                detectedObjects = detected,
                recommendedSettings = recommendedSettings(sceneType)
            )
        } catch (e: Throwable) {
            Log.e("AiBackend", "detectScene(bitmap) failed", e)
            SceneDetectionResult(
                sceneType = SceneType.AUTO,
                confidence = 0f,
                detectedObjects = emptyList(),
                recommendedSettings = CameraSettings(null, null, null)
            )
        }
    }

    /**
     * Composition analysis using ML Kit face detector + OpenCV geometric analysis.
     */
    suspend fun analyzeComposition(imageProxy: ImageProxy, sceneType: SceneType): CompositionAnalysisResult {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            return CompositionAnalysisResult(
                success = false,
                suggestions = emptyList(),
                compositionScore = 0f,
                idealScore = 0.90f,
                detectedFaces = emptyList()
            )
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
        )

        return try {
            val faces = detector.process(image).await()

            // 如果有OpenCV支持，使用高级分析
            if (OpenCvHelper.isReady()) {
                // 将ImageProxy转换为Bitmap用于OpenCV分析
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    val detailedResult = CompositionEngine.analyzeAdvanced(bitmap, faces, sceneType)
                    bitmap.recycle()
                    return CompositionEngine.toLegacyResult(detailedResult)
                }
            }

            // 降级到基础分析
            basicCompositionAnalysis(faces, imageProxy.width, imageProxy.height, sceneType)
        } catch (e: Throwable) {
            Log.e("AiBackend", "analyzeComposition failed", e)
            CompositionAnalysisResult(
                success = false,
                suggestions = emptyList(),
                compositionScore = 0f,
                idealScore = 0.90f,
                detectedFaces = emptyList()
            )
        }
    }

    /**
     * 基础构图分析（无OpenCV时使用）
     */
    private fun basicCompositionAnalysis(
        faces: List<Face>,
        width: Int,
        height: Int,
        sceneType: SceneType
    ): CompositionAnalysisResult {
        if (faces.isEmpty()) {
            return CompositionAnalysisResult(
                success = true,
                suggestions = listOf(
                    CompositionSuggestion(
                        type = SuggestionType.POSITION,
                        message = "将主体尽量放在画面三分线附近",
                        confidence = 0.5f,
                        priority = SuggestionPriority.LOW
                    )
                ),
                compositionScore = 0.5f,
                idealScore = 0.90f,
                detectedFaces = emptyList()
            )
        }

        val w = max(1, width).toFloat()
        val h = max(1, height).toFloat()

        val main = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }
        val leftEye = main.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = main.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val eyeY = if (leftEye != null && rightEye != null) {
            (leftEye.y + rightEye.y) / 2f
        } else {
            main.boundingBox.centerY().toFloat()
        }

        val relativeEyeY = (eyeY / h).coerceIn(0f, 1f)
        val idealEyeY = when (sceneType) {
            SceneType.PORTRAIT -> 0.33f
            else -> 0.40f
        }
        val diff = idealEyeY - relativeEyeY

        val relFaceWidth = (main.boundingBox.width().toFloat() / w).coerceIn(0f, 1f)

        val suggestions = buildList {
            if (diff > 0.05f) {
                add(
                    CompositionSuggestion(
                        type = SuggestionType.POSITION,
                        message = "向上移动一点，将眼睛靠近上方三分线",
                        confidence = 0.85f,
                        priority = SuggestionPriority.HIGH
                    )
                )
            } else if (diff < -0.05f) {
                add(
                    CompositionSuggestion(
                        type = SuggestionType.POSITION,
                        message = "向下移动一点，将眼睛靠近上方三分线",
                        confidence = 0.85f,
                        priority = SuggestionPriority.HIGH
                    )
                )
            }

            if (relFaceWidth < 0.30f) {
                add(
                    CompositionSuggestion(
                        type = SuggestionType.DISTANCE,
                        message = "靠近半步，主体更突出",
                        confidence = 0.72f,
                        priority = SuggestionPriority.MEDIUM
                    )
                )
            } else if (relFaceWidth > 0.65f) {
                add(
                    CompositionSuggestion(
                        type = SuggestionType.DISTANCE,
                        message = "后退半步，留更多空间",
                        confidence = 0.72f,
                        priority = SuggestionPriority.MEDIUM
                    )
                )
            }
        }

        val score = (1.0f - min(abs(diff) * 2f, 1.0f))
            .coerceIn(0f, 1f)

        val detectedFaces = listOf(
            DetectedFace(
                x = (main.boundingBox.left / w).coerceIn(0f, 1f),
                y = (main.boundingBox.top / h).coerceIn(0f, 1f),
                width = (main.boundingBox.width() / w).coerceIn(0f, 1f),
                height = (main.boundingBox.height() / h).coerceIn(0f, 1f),
                eyeY = relativeEyeY,
                confidence = (main.trackingId?.toFloat() ?: 0f)
            )
        )

        return CompositionAnalysisResult(
            success = true,
            suggestions = suggestions,
            compositionScore = score,
            idealScore = 0.90f,
            detectedFaces = detectedFaces
        )
    }

    /**
     * 将ImageProxy转换为Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("AiBackend", "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }

    /**
     * Composition analysis from a [Bitmap] (useful for PreviewView.bitmap).
     * 使用 OpenCV 增强分析
     */
    suspend fun analyzeComposition(bitmap: Bitmap, sceneType: SceneType): CompositionAnalysisResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
        )

        return try {
            val faces = detector.process(image).await()

            // 如果有OpenCV支持，使用高级分析
            if (OpenCvHelper.isReady()) {
                val detailedResult = CompositionEngine.analyzeAdvanced(bitmap, faces, sceneType)
                return CompositionEngine.toLegacyResult(detailedResult)
            }

            // 降级到基础分析
            basicCompositionAnalysis(faces, bitmap.width, bitmap.height, sceneType)
        } catch (e: Throwable) {
            Log.e("AiBackend", "analyzeComposition(bitmap) failed", e)
            CompositionAnalysisResult(
                success = false,
                suggestions = emptyList(),
                compositionScore = 0f,
                idealScore = 0.90f,
                detectedFaces = emptyList()
            )
        }
    }

    private fun inferScene(labels: List<com.google.mlkit.vision.label.ImageLabel>): Pair<SceneType, Float> {
        var bestType = SceneType.AUTO
        var best = 0f

        fun consider(type: SceneType, confidence: Float) {
            if (confidence > best) {
                bestType = type
                best = confidence
            }
        }

        labels.forEach { l ->
            val t = l.text.lowercase()
            when {
                "person" in t || "face" in t -> consider(SceneType.PORTRAIT, l.confidence)
                "food" in t || "meal" in t -> consider(SceneType.FOOD, l.confidence)
                "landscape" in t || "mountain" in t || "sky" in t || "nature" in t -> consider(SceneType.LANDSCAPE, l.confidence)
                "night" in t || "dark" in t -> consider(SceneType.NIGHT, l.confidence)
                "building" in t || "architecture" in t -> consider(SceneType.ARCHITECTURE, l.confidence)
            }
        }

        return bestType to best
    }

    private fun recommendedSettings(sceneType: SceneType): CameraSettings {
        return when (sceneType) {
            SceneType.PORTRAIT -> CameraSettings(100, "1/125s", "f/1.8")
            SceneType.LANDSCAPE -> CameraSettings(200, "1/250s", "f/8.0")
            SceneType.FOOD -> CameraSettings(200, "1/125s", "f/2.0")
            SceneType.NIGHT -> CameraSettings(800, "1/60s", "f/1.8")
            SceneType.ARCHITECTURE -> CameraSettings(200, "1/250s", "f/5.6")
            SceneType.AUTO -> CameraSettings(null, null, null)
        }
    }
}

