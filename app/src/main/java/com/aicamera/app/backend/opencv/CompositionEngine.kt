package com.aicamera.app.backend.opencv

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.aicamera.app.backend.models.CompositionAnalysis
import com.aicamera.app.backend.models.CompositionAnalysisResult
import com.aicamera.app.backend.models.CompositionCommand
import com.aicamera.app.backend.models.CompositionRule
import com.aicamera.app.backend.models.DetectedFace
import com.aicamera.app.backend.models.DetailedCompositionResult
import com.aicamera.app.backend.models.HorizonInfo
import com.aicamera.app.backend.models.InstructionDirection
import com.aicamera.app.backend.models.LineInfo
import com.aicamera.app.backend.models.OpenCvMetrics
import com.aicamera.app.backend.models.SceneCompositionConfig
import com.aicamera.app.backend.models.SceneCompositionConfigs
import com.aicamera.app.backend.models.SceneType
import com.aicamera.app.backend.models.SubjectPosition
import com.aicamera.app.backend.models.SubjectType
import com.aicamera.app.backend.models.SuggestionPriority
import com.aicamera.app.backend.models.SuggestionType
import com.google.mlkit.vision.face.Face
import org.opencv.core.Mat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

object CompositionEngine {
    private const val TAG = "CompositionEngine"

    private const val THIRD_1 = 0.333f
    private const val THIRD_2 = 0.667f
    private const val HORIZON_ANGLE_THRESHOLD = 3f
    private const val POSITION_TOLERANCE = 0.08f

    fun analyzeAdvanced(
        bitmap: Bitmap,
        faces: List<Face>,
        sceneType: SceneType
    ): DetailedCompositionResult {
        val config = SceneCompositionConfigs.getConfig(sceneType)

        return try {
            // 转换为 Mat
            val mat = OpenCvHelper.bitmapToMat(bitmap)
            val grayMat = OpenCvHelper.toGray(mat)
            val edges = OpenCvHelper.detectEdges(grayMat)

            // 1. 几何分析
            val horizonAngle = OpenCvHelper.detectHorizonAngle(edges)
            val horizonInfo = HorizonInfo(
                detected = abs(horizonAngle) > 0.5f,
                angle = horizonAngle,
                yPosition = detectHorizonPosition(edges),
                isLevel = abs(horizonAngle) < HORIZON_ANGLE_THRESHOLD
            )

            // 2. 检测引导线
            val leadingLines = OpenCvHelper.detectLeadingLines(edges, bitmap.width, bitmap.height).map { line ->
                LineInfo(
                    startX = line.startX.toFloat(),
                    startY = line.startY.toFloat(),
                    endX = line.endX.toFloat(),
                    endY = line.endY.toFloat(),
                    angle = line.angle.toFloat(),
                    type = when {
                        line.isHorizontal() -> com.aicamera.app.backend.models.LineType.HORIZONTAL
                        line.isVertical() -> com.aicamera.app.backend.models.LineType.VERTICAL
                        else -> com.aicamera.app.backend.models.LineType.LEADING
                    }
                )
            }

            // 3. 对称性分析
            val symmetryScore = OpenCvHelper.detectSymmetry(mat)

            // 4. 前景层次
            val foregroundRegions = OpenCvHelper.detectForegroundRegions(edges)

            // 5. 主体位置
            val subjectPosition = extractSubjectPosition(faces, bitmap.width, bitmap.height)

            // 6. 头顶留白
            val headroomRatio = if (faces.isNotEmpty()) {
                val mainFace = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }
                mainFace.boundingBox.top.toFloat() / bitmap.height
            } else null

            // 7. 主体占比
            val subjectSizeRatio = if (faces.isNotEmpty()) {
                val mainFace = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }
                val area = mainFace.boundingBox.width() * mainFace.boundingBox.height()
                area.toFloat() / (bitmap.width * bitmap.height)
            } else null

            // 组装分析结果
            val analysis = CompositionAnalysis(
                subjectPosition = subjectPosition,
                horizonInfo = horizonInfo,
                leadingLines = leadingLines,
                symmetryScore = symmetryScore,
                balanceScore = calculateBalanceScore(subjectPosition, leadingLines),
                headroomRatio = headroomRatio,
                subjectSizeRatio = subjectSizeRatio
            )

            val commands = generateCommands(analysis, config, faces.isNotEmpty())
            val detectedRule = determinePrimaryRule(analysis, config)
            val score = calculateOverallScore(analysis, config)

            val metrics = OpenCvMetrics(
                edgeDensity = if (foregroundRegions.isNotEmpty()) foregroundRegions.map { it.edgeDensity }.average().toFloat() else 0f,
                colorVariance = 0f,
                detectedLines = leadingLines.size,
                foregroundRegions = foregroundRegions.count { it.hasContent }
            )

            DetailedCompositionResult(
                success = true,
                score = score,
                detectedRule = detectedRule,
                commands = commands,
                analysis = analysis,
                rawMetrics = metrics
            )

        } catch (e: Exception) {
            Log.e(TAG, "Advanced analysis failed", e)
            fallbackToBasic(bitmap, faces, sceneType)
        } finally {
            // 释放 Mat 资源
            OpenCvHelper.release()
        }
    }

    private fun generateCommands(
        analysis: CompositionAnalysis,
        config: SceneCompositionConfig,
        hasFaces: Boolean
    ): List<CompositionCommand> {
        val commands = mutableListOf<CompositionCommand>()

        // 1. 水平线校正
        analysis.horizonInfo?.let { horizon ->
            if (horizon.detected && !horizon.isLevel) {
                val direction = if (horizon.angle > 0) {
                    InstructionDirection.ROTATE_RIGHT
                } else {
                    InstructionDirection.ROTATE_LEFT
                }
                val magnitude = min(abs(horizon.angle) / 10f, 1f)
                commands.add(CompositionCommand(
                    direction = direction,
                    magnitude = magnitude,
                    reason = "地平线倾斜",
                    priority = SuggestionPriority.HIGH,
                    displayText = if (direction == InstructionDirection.ROTATE_LEFT) "左转${abs(horizon.angle).roundToInt()}°" else "右转${abs(horizon.angle).roundToInt()}°"
                ))
            }
        }

        // 2. 主体位置调整
        analysis.subjectPosition?.let { subject ->
            val idealY = config.idealSubjectY
            val diffY = idealY - subject.centerY

            if (abs(diffY) > POSITION_TOLERANCE) {
                val direction = if (diffY > 0) {
                    InstructionDirection.MOVE_DOWN
                } else {
                    InstructionDirection.MOVE_UP
                }
                val magnitude = min(abs(diffY) * 2f, 1f)
                val priority = if (abs(diffY) > 0.15f) SuggestionPriority.HIGH else SuggestionPriority.MEDIUM

                commands.add(CompositionCommand(
                    direction = direction,
                    magnitude = magnitude,
                    reason = "主体垂直位置优化",
                    priority = priority,
                    displayText = generatePositionText(direction, magnitude, true)
                ))
            }

            // 水平位置
            val diffX1 = abs(subject.centerX - THIRD_1)
            val diffX2 = abs(subject.centerX - THIRD_2)
            val diffXCenter = abs(subject.centerX - 0.5f)

            if (diffX1 > POSITION_TOLERANCE && diffX2 > POSITION_TOLERANCE && diffXCenter > 0.1f) {
                val targetX = if (diffX1 < diffX2) THIRD_1 else THIRD_2
                val direction = if (targetX > subject.centerX) {
                    InstructionDirection.MOVE_RIGHT
                } else {
                    InstructionDirection.MOVE_LEFT
                }

                commands.add(CompositionCommand(
                    direction = direction,
                    magnitude = 0.5f,
                    reason = "移至三分线交点",
                    priority = SuggestionPriority.MEDIUM,
                    displayText = if (targetX == THIRD_1) "主体左移放左交点" else "主体右移放右交点"
                ))
            }
        }

        // 3. 距离调整
        analysis.subjectSizeRatio?.let { ratio ->
            if (ratio < config.idealSubjectSize.min) {
                commands.add(CompositionCommand(
                    direction = InstructionDirection.MOVE_CLOSER,
                    magnitude = (config.idealSubjectSize.min - ratio) * 2f,
                    reason = "主体占比偏小",
                    priority = SuggestionPriority.MEDIUM,
                    displayText = "靠近半步主体更突出"
                ))
            } else if (ratio > config.idealSubjectSize.max) {
                commands.add(CompositionCommand(
                    direction = InstructionDirection.MOVE_BACK,
                    magnitude = (ratio - config.idealSubjectSize.max) * 2f,
                    reason = "主体占比偏大",
                    priority = SuggestionPriority.MEDIUM,
                    displayText = "后退半步留更多空间"
                ))
            }
        }

        // 4. 头顶留白
        analysis.headroomRatio?.let { headroom ->
            if (hasFaces && config.sceneType == SceneType.PORTRAIT) {
                val idealHeadroom = 0.2f
                val diff = idealHeadroom - headroom
                if (abs(diff) > 0.08f) {
                    val direction = if (diff > 0) {
                        InstructionDirection.TILT_UP
                    } else {
                        InstructionDirection.TILT_DOWN
                    }
                    commands.add(CompositionCommand(
                        direction = direction,
                        magnitude = min(abs(diff) * 3f, 1f),
                        reason = "头顶留白优化",
                        priority = SuggestionPriority.LOW,
                        displayText = if (diff > 0) "手机微抬增加头顶空间" else "手机略降减少头顶空间"
                    ))
                }
            }
        }

        // 5. 引导线
        if (analysis.leadingLines.isNotEmpty() &&
            config.sceneType in listOf(SceneType.LANDSCAPE, SceneType.ARCHITECTURE)) {
            val bestLine = analysis.leadingLines.first()
            if (bestLine.type == com.aicamera.app.backend.models.LineType.LEADING) {
                commands.add(CompositionCommand(
                    direction = InstructionDirection.ADJUST_ANGLE,
                    magnitude = 0.6f,
                    reason = "利用引导线增强纵深感",
                    priority = SuggestionPriority.LOW,
                    displayText = "利用线条引导视线深入"
                ))
            }
        }

        // 6. 场景特定指令
        when (config.sceneType) {
            SceneType.LANDSCAPE -> {
                analysis.horizonInfo?.let { horizon ->
                    if (horizon.yPosition != null) {
                        when (config.horizonPreference) {
                            com.aicamera.app.backend.models.HorizonPreference.LOW -> {
                                if (horizon.yPosition > 0.4f) {
                                    commands.add(CompositionCommand(
                                        direction = InstructionDirection.TILT_DOWN,
                                        magnitude = 0.5f,
                                        reason = "天空占比",
                                        priority = SuggestionPriority.LOW,
                                        displayText = "手机略抬天空占2/3"
                                    ))
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            SceneType.NIGHT -> {
                commands.add(CompositionCommand(
                    direction = InstructionDirection.HOLD_STEADY,
                    magnitude = 1f,
                    reason = "夜景防抖",
                    priority = SuggestionPriority.HIGH,
                    displayText = "双手握稳，屏息拍摄"
                ))
            }
            SceneType.FOOD -> {
                commands.add(CompositionCommand(
                    direction = InstructionDirection.TILT_DOWN,
                    magnitude = 0.7f,
                    reason = "俯拍角度",
                    priority = SuggestionPriority.MEDIUM,
                    displayText = "俯拍45°效果最佳"
                ))
            }
            else -> {}
        }

        return commands.sortedBy { it.priority.ordinal }
    }

    private fun generatePositionText(
        direction: InstructionDirection,
        magnitude: Float,
        isVertical: Boolean
    ): String {
        val amount = when {
            magnitude > 0.7 -> if (isVertical) "一格" else "少许"
            magnitude > 0.4 -> "一点"
            else -> "微移"
        }
        return when (direction) {
            InstructionDirection.MOVE_UP -> "上移$amount"
            InstructionDirection.MOVE_DOWN -> "下移$amount"
            InstructionDirection.MOVE_LEFT -> "左移$amount"
            InstructionDirection.MOVE_RIGHT -> "右移$amount"
            else -> "调整位置"
        }
    }

    private fun extractSubjectPosition(
        faces: List<Face>,
        imageWidth: Int,
        imageHeight: Int
    ): SubjectPosition? {
        if (faces.isEmpty()) return null

        val mainFace = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }
        val box = mainFace.boundingBox

        return SubjectPosition(
            centerX = (box.left + box.width() / 2f) / imageWidth,
            centerY = (box.top + box.height() / 2f) / imageHeight,
            width = box.width().toFloat() / imageWidth,
            height = box.height().toFloat() / imageHeight,
            type = SubjectType.FACE
        )
    }

    private fun detectHorizonPosition(edges: Mat): Float? {
        val width = edges.cols()
        val height = edges.rows()

        val roiHeight = height / 3
        val startY = roiHeight
        val lines = mutableListOf<Float>()

        val pixels = IntArray(width * height)
        edges.get(0, 0, pixels)

        for (y in startY until (startY + roiHeight) step 10) {
            var edgeCount = 0
            for (x in 0 until width step 5) {
                val pixelValue = pixels[y * width + x].toDouble()
                if (pixelValue > 128.0) {
                    edgeCount++
                }
            }
            if (edgeCount > width / 10) {
                lines.add(y.toFloat())
            }
        }

        return if (lines.isNotEmpty()) lines.average().toFloat() / height else null
    }

    private fun calculateBalanceScore(
        subjectPosition: SubjectPosition?,
        leadingLines: List<LineInfo>
    ): Float {
        var score = 0.5f
        subjectPosition?.let {
            val centerDist = abs(it.centerX - 0.5f)
            score += (0.5f - centerDist) * 0.3f
        }
        return score.coerceIn(0f, 1f)
    }

    private fun determinePrimaryRule(
        analysis: CompositionAnalysis,
        config: SceneCompositionConfig
    ): CompositionRule? {
        return when {
            analysis.symmetryScore > 0.7f -> CompositionRule.CENTER_SYMMETRY
            analysis.leadingLines.size >= 2 -> CompositionRule.LEADING_LINES
            analysis.horizonInfo?.isLevel == true &&
                analysis.horizonInfo.yPosition != null -> CompositionRule.HORIZON_PLACEMENT
            else -> CompositionRule.RULE_OF_THIRDS
        }
    }

    private fun calculateOverallScore(
        analysis: CompositionAnalysis,
        config: SceneCompositionConfig
    ): Float {
        var score = 60f

        analysis.horizonInfo?.let {
            if (it.isLevel) score += 15f
        }

        analysis.subjectPosition?.let { subject ->
            val yDiff = abs(subject.centerY - config.idealSubjectY)
            score += (1f - yDiff) * 15f
        }

        analysis.subjectSizeRatio?.let { ratio ->
            if (ratio in config.idealSubjectSize.min..config.idealSubjectSize.max) {
                score += 10f
            }
        }

        score += analysis.symmetryScore * 10f
        return score.coerceIn(0f, 100f)
    }

    private fun fallbackToBasic(
        bitmap: Bitmap,
        faces: List<Face>,
        sceneType: SceneType
    ): DetailedCompositionResult {
        val subjectPosition = extractSubjectPosition(faces, bitmap.width, bitmap.height)
        val config = SceneCompositionConfigs.getConfig(sceneType)

        val analysis = CompositionAnalysis(
            subjectPosition = subjectPosition,
            horizonInfo = null,
            leadingLines = emptyList(),
            symmetryScore = 0.5f,
            balanceScore = 0.5f,
            headroomRatio = null,
            subjectSizeRatio = subjectPosition?.let { it.width * it.height }
        )

        val commands = generateCommands(analysis, config, faces.isNotEmpty())

        return DetailedCompositionResult(
            success = true,
            score = 50f,
            detectedRule = null,
            commands = commands,
            analysis = analysis,
            rawMetrics = null
        )
    }

    fun toLegacyResult(detailed: DetailedCompositionResult): CompositionAnalysisResult {
        val suggestions = detailed.commands.map { cmd ->
            com.aicamera.app.backend.models.CompositionSuggestion(
                type = when (cmd.direction) {
                    InstructionDirection.ROTATE_LEFT, InstructionDirection.ROTATE_RIGHT -> SuggestionType.ANGLE
                    InstructionDirection.MOVE_CLOSER, InstructionDirection.MOVE_BACK -> SuggestionType.DISTANCE
                    else -> SuggestionType.POSITION
                },
                message = cmd.displayText,
                confidence = cmd.magnitude,
                priority = cmd.priority
            )
        }

        return CompositionAnalysisResult(
            success = detailed.success,
            suggestions = suggestions,
            compositionScore = detailed.score / 100f,
            idealScore = 0.85f,
            detectedFaces = detailed.analysis.subjectPosition?.let { subject ->
                listOf(
                    DetectedFace(
                        x = subject.centerX - subject.width / 2,
                        y = subject.centerY - subject.height / 2,
                        width = subject.width,
                        height = subject.height,
                        eyeY = subject.centerY - subject.height * 0.2f,
                        confidence = 0.9f
                    )
                )
            } ?: emptyList()
        )
    }
}
