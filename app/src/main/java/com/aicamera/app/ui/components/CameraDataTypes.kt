package com.aicamera.app.ui.components

/** AI 建议来源 */
enum class TipSource {
    NONE,
    CLOUD,
    LOCAL
}

/** 比例切换动画状态 */
enum class TransitionState {
    IDLE,
    BLURRING,
    CLEARING
}

/** 屏幕布局信息 */
data class ScreenInfo(val left: Float, val top: Float, val width: Float, val height: Float)

/** 取景框边界 */
data class ViewfinderBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) {
    companion object {
        val ZERO = ViewfinderBounds()

        fun lerp(start: ViewfinderBounds, end: ViewfinderBounds, progress: Float): ViewfinderBounds {
            return ViewfinderBounds(
                left = start.left + (end.left - start.left) * progress,
                top = start.top + (end.top - start.top) * progress,
                width = start.width + (end.width - start.width) * progress,
                height = start.height + (end.height - start.height) * progress
            )
        }
    }
}
