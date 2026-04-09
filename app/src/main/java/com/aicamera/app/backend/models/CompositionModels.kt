package com.aicamera.app.backend.models

/**
 * 构图规则类型
 */
enum class CompositionRule {
    RULE_OF_THIRDS,      // 三分法
    GOLDEN_RATIO,        // 黄金分割
    CENTER_SYMMETRY,     // 中心对称
    LEADING_LINES,       // 引导线
    FRAME_IN_FRAME,      // 框架构图
    FOREGROUND_INTEREST, // 前景层次
    HORIZON_PLACEMENT,   // 地平线位置
    HEADROOM_BALANCE,    // 留白平衡
    DIAGONAL,            // 对角线构图
    TRIANGLE,            // 三角形构图
    VERTICAL             // 垂直线构图
}

/**
 * 指令方向类型 - 用于用户可执行的操作
 */
enum class InstructionDirection {
    MOVE_UP,        // 上移
    MOVE_DOWN,      // 下移
    MOVE_LEFT,      // 左移
    MOVE_RIGHT,     // 右移
    MOVE_CLOSER,    // 靠近
    MOVE_BACK,      // 后退
    TILT_UP,        // 抬头（手机上抬）
    TILT_DOWN,      // 低头（手机下倾）
    ROTATE_LEFT,    // 左转
    ROTATE_RIGHT,   // 右转
    HOLD_STEADY,    // 保持稳定
    ADJUST_ANGLE    // 调整角度
}

/**
 * 用户可执行的构图指令
 */
data class CompositionCommand(
    val direction: InstructionDirection,
    val magnitude: Float,           // 幅度 0.0-1.0
    val reason: String,             // 原因简述
    val priority: SuggestionPriority,
    val displayText: String         // 显示给用户的文字（短指令）
)

/**
 * 详细的构图分析结果
 */
data class DetailedCompositionResult(
    val success: Boolean,
    val score: Float,                          // 构图评分 0-100
    val detectedRule: CompositionRule?,        // 主要构图规则
    val commands: List<CompositionCommand>,    // 可执行指令列表
    val analysis: CompositionAnalysis,         // 详细分析数据
    val rawMetrics: OpenCvMetrics?             // OpenCV原始数据
)

/**
 * 构图分析数据
 */
data class CompositionAnalysis(
    val subjectPosition: SubjectPosition?,     // 主体位置
    val horizonInfo: HorizonInfo?,             // 地平线信息
    val leadingLines: List<LineInfo>,          // 引导线信息
    val symmetryScore: Float,                  // 对称性分数
    val balanceScore: Float,                   // 平衡性分数
    val headroomRatio: Float?,                 // 头顶留白比例
    val subjectSizeRatio: Float?               // 主体占画面比例
)

/**
 * 主体位置
 */
data class SubjectPosition(
    val centerX: Float,        // 0.0-1.0
    val centerY: Float,        // 0.0-1.0
    val width: Float,          // 0.0-1.0
    val height: Float,         // 0.0-1.0
    val type: SubjectType
)

/**
 * 地平线信息
 */
data class HorizonInfo(
    val detected: Boolean,
    val angle: Float,          // 倾斜角度（度）
    val yPosition: Float?,     // 地平线位置（0.0-1.0）
    val isLevel: Boolean       // 是否水平
)

/**
 * 线段信息（引导线/地平线）
 */
data class LineInfo(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val angle: Float,
    val type: LineType
)

enum class LineType {
    HORIZONTAL,     // 水平线（地平线）
    VERTICAL,       // 垂直线（建筑）
    DIAGONAL,       // 对角线
    LEADING         // 引导线
}

/**
 * OpenCV 原始指标
 */
data class OpenCvMetrics(
    val edgeDensity: Float,
    val colorVariance: Float,
    val detectedLines: Int,
    val foregroundRegions: Int
)

/**
 * 场景特定的构图配置
 */
data class SceneCompositionConfig(
    val sceneType: SceneType,
    val preferredRules: List<CompositionRule>,
    val idealSubjectY: Float,              // 理想的主体Y位置
    val idealSubjectSize: FloatRange,      // 理想的画面占比
    val horizonPreference: HorizonPreference,
    val enabledCommands: Set<InstructionDirection>
)

data class FloatRange(val min: Float, val max: Float) {
    operator fun contains(value: Float): Boolean = value in min..max
}

enum class HorizonPreference {
    LOW,        // 地平线偏下（天空多）
    CENTER,     // 地平线居中
    HIGH,       // 地平线偏上（地面多）
    NONE        // 不关心
}

/**
 * 预设的场景构图配置
 */
object SceneCompositionConfigs {
    val PORTRAIT = SceneCompositionConfig(
        sceneType = SceneType.PORTRAIT,
        preferredRules = listOf(
            CompositionRule.RULE_OF_THIRDS,
            CompositionRule.HEADROOM_BALANCE,
            CompositionRule.FRAME_IN_FRAME
        ),
        idealSubjectY = 0.38f,  // 眼睛在上方三分线
        idealSubjectSize = FloatRange(0.25f, 0.45f),
        horizonPreference = HorizonPreference.NONE,
        enabledCommands = setOf(
            InstructionDirection.MOVE_UP,
            InstructionDirection.MOVE_DOWN,
            InstructionDirection.MOVE_LEFT,
            InstructionDirection.MOVE_RIGHT,
            InstructionDirection.MOVE_CLOSER,
            InstructionDirection.MOVE_BACK,
            InstructionDirection.ROTATE_LEFT,
            InstructionDirection.ROTATE_RIGHT
        )
    )

    val LANDSCAPE = SceneCompositionConfig(
        sceneType = SceneType.LANDSCAPE,
        preferredRules = listOf(
            CompositionRule.HORIZON_PLACEMENT,
            CompositionRule.LEADING_LINES,
            CompositionRule.FOREGROUND_INTEREST,
            CompositionRule.RULE_OF_THIRDS
        ),
        idealSubjectY = 0.5f,
        idealSubjectSize = FloatRange(0.1f, 0.3f),
        horizonPreference = HorizonPreference.LOW,  // 天空占2/3
        enabledCommands = setOf(
            InstructionDirection.TILT_UP,
            InstructionDirection.TILT_DOWN,
            InstructionDirection.ROTATE_LEFT,
            InstructionDirection.ROTATE_RIGHT,
            InstructionDirection.MOVE_CLOSER,
            InstructionDirection.MOVE_BACK
        )
    )

    val FOOD = SceneCompositionConfig(
        sceneType = SceneType.FOOD,
        preferredRules = listOf(
            CompositionRule.RULE_OF_THIRDS,
            CompositionRule.DIAGONAL,
            CompositionRule.FRAME_IN_FRAME
        ),
        idealSubjectY = 0.5f,
        idealSubjectSize = FloatRange(0.4f, 0.7f),
        horizonPreference = HorizonPreference.NONE,
        enabledCommands = setOf(
            InstructionDirection.TILT_UP,
            InstructionDirection.TILT_DOWN,
            InstructionDirection.ROTATE_LEFT,
            InstructionDirection.ROTATE_RIGHT,
            InstructionDirection.MOVE_UP,
            InstructionDirection.MOVE_DOWN
        )
    )

    val NIGHT = SceneCompositionConfig(
        sceneType = SceneType.NIGHT,
        preferredRules = listOf(
            CompositionRule.CENTER_SYMMETRY,
            CompositionRule.LEADING_LINES,
            CompositionRule.RULE_OF_THIRDS
        ),
        idealSubjectY = 0.5f,
        idealSubjectSize = FloatRange(0.15f, 0.4f),
        horizonPreference = HorizonPreference.CENTER,
        enabledCommands = setOf(
            InstructionDirection.HOLD_STEADY,
            InstructionDirection.ROTATE_LEFT,
            InstructionDirection.ROTATE_RIGHT,
            InstructionDirection.TILT_UP,
            InstructionDirection.TILT_DOWN
        )
    )

    val ARCHITECTURE = SceneCompositionConfig(
        sceneType = SceneType.ARCHITECTURE,
        preferredRules = listOf(
            CompositionRule.CENTER_SYMMETRY,
            CompositionRule.LEADING_LINES,
            CompositionRule.VERTICAL
        ),
        idealSubjectY = 0.5f,
        idealSubjectSize = FloatRange(0.3f, 0.6f),
        horizonPreference = HorizonPreference.NONE,
        enabledCommands = setOf(
            InstructionDirection.ROTATE_LEFT,
            InstructionDirection.ROTATE_RIGHT,
            InstructionDirection.TILT_UP,
            InstructionDirection.TILT_DOWN,
            InstructionDirection.MOVE_BACK
        )
    )

    fun getConfig(sceneType: SceneType): SceneCompositionConfig {
        return when (sceneType) {
            SceneType.PORTRAIT -> PORTRAIT
            SceneType.LANDSCAPE -> LANDSCAPE
            SceneType.FOOD -> FOOD
            SceneType.NIGHT -> NIGHT
            SceneType.ARCHITECTURE -> ARCHITECTURE
            SceneType.AUTO -> PORTRAIT // 默认使用人像配置
        }
    }
}
