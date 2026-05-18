package com.aicamera.app.backend.models

/**
 * Camera params shown on UI.
 */
data class CameraParams(
    val iso: String,
    val shutter: String,
    val aperture: String
)

enum class FlashMode { AUTO, ON, OFF }

enum class SceneType {
    PORTRAIT,
    LANDSCAPE,
    FOOD,
    NIGHT,
    ARCHITECTURE,
    AUTO
}

data class CameraSettings(
    val iso: Int?,
    val shutterSpeed: String?,
    val aperture: String?
)

data class SceneDetectionResult(
    val sceneType: SceneType,
    val confidence: Float,
    val detectedObjects: List<String>,
    val recommendedSettings: CameraSettings
)

enum class SuggestionType { POSITION, ANGLE, DISTANCE, LIGHTING }
enum class SuggestionPriority { HIGH, MEDIUM, LOW }

data class CompositionSuggestion(
    val type: SuggestionType,
    val message: String,
    val confidence: Float,
    val priority: SuggestionPriority
)

data class DetectedFace(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val eyeY: Float,
    val confidence: Float
)

data class CompositionAnalysisResult(
    val success: Boolean,
    val suggestions: List<CompositionSuggestion>,
    val compositionScore: Float,
    val idealScore: Float,
    val detectedFaces: List<DetectedFace>
)

enum class CropMode { AUTO, PORTRAIT, LANDSCAPE, SQUARE }
enum class SubjectType { FACE, UPPER_BODY, FULL_BODY, OBJECT, TEXT, GENERIC, UNKNOWN }

data class CropRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

data class SmartCropResult(
    val success: Boolean,
    val cropRect: CropRect,
    val confidence: Float,
    val suggestion: String,
    val detectedSubjects: List<SubjectType>,
    val aspectRatio: String
)

data class ColorAdjustmentParams(
    val exposure: Float,
    val contrast: Float,
    val saturation: Float,
    val sharpness: Float,
    val temperature: Float,
    val highlights: Float,
    val shadows: Float = 0.5f  // 新增阴影参数，默认 0.5
)

data class AIEnhanceResult(
    val success: Boolean,
    val params: ColorAdjustmentParams,
    val detectedInfo: String,
    val confidence: Float
)

