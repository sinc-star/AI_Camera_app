package com.aicamera.app.ui.screens

import android.graphics.Bitmap
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.view.PreviewView
import com.aicamera.app.ui.components.ScreenInfo
import com.aicamera.app.ui.components.ViewfinderBounds

/** 将画幅比例值映射到 CameraX AspectRatio 常量 */
fun toCameraAspectRatio(ratioPortrait: Float): Int =
    if (ratioPortrait == 0.5625f || ratioPortrait < 0f) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3

/** 全屏模式使用 FILL_CENTER 填满屏幕，其他模式使用 FIT_CENTER 完整显示 */
fun toPreviewScaleType(ratioPortrait: Float): PreviewView.ScaleType =
    if (ratioPortrait < 0f) PreviewView.ScaleType.FILL_CENTER
    else PreviewView.ScaleType.FIT_CENTER

/** 从 PreviewView 捕获降采样 bitmap，减少内存压力和处理时间 */
fun PreviewView.captureScaledBitmap(maxEdge: Int = 480): Bitmap? {
    val original = this.bitmap ?: return null
    val width = original.width
    val height = original.height
    val maxDimension = maxOf(width, height)
    if (maxDimension <= maxEdge) return original
    val scale = maxEdge.toFloat() / maxDimension
    return Bitmap.createScaledBitmap(
        original,
        (width * scale).toInt(),
        (height * scale).toInt(),
        true
    ).also { original.recycle() }
}

/** 计算相机图像在 PreviewView 中的实际显示区域（FIT_CENTER 逻辑） */
fun computeCameraImageBounds(
    previewViewWidth: Float, previewViewHeight: Float,
    cameraOutputWidth: Int, cameraOutputHeight: Int,
    isFullscreen: Boolean = false
): ViewfinderBounds {
    if (cameraOutputWidth <= 0 || cameraOutputHeight <= 0) return ViewfinderBounds.ZERO

    val camRatio = cameraOutputWidth.toFloat() / cameraOutputHeight
    val pvRatio = if (previewViewHeight > 0f) previewViewWidth / previewViewHeight else 0f

    val (camW, camH, camL, camT) = if (isFullscreen) {
        if (camRatio > pvRatio) {
            val w = previewViewHeight * camRatio
            val h = previewViewHeight
            val l = (previewViewWidth - w) / 2f
            val t = 0f
            listOf(w, h, l, t)
        } else {
            val w = previewViewWidth
            val h = previewViewWidth / camRatio
            val l = 0f
            val t = (previewViewHeight - h) / 2f
            listOf(w, h, l, t)
        }
    } else {
        if (camRatio > pvRatio) {
            val w = previewViewWidth
            val h = previewViewWidth / camRatio
            val l = 0f
            val t = (previewViewHeight - h) / 2f
            listOf(w, h, l, t)
        } else {
            val w = previewViewHeight * camRatio
            val h = previewViewHeight
            val l = (previewViewWidth - w) / 2f
            val t = 0f
            listOf(w, h, l, t)
        }
    }

    return ViewfinderBounds(
        left = camL,
        top = camT,
        width = camW,
        height = camH
    )
}

/** 根据屏幕尺寸和画幅比例计算取景框边界 */
fun computeViewfinderBounds(
    left: Float, top: Float,
    screenWidth: Float, screenHeight: Float,
    aspectRatioPortrait: Float,
    offsetYPx: Float,
    cameraOutputWidth: Int = 0,
    cameraOutputHeight: Int = 0,
    displayRotation: Int = Surface.ROTATION_0
): ViewfinderBounds {
    if (screenWidth <= 0f || screenHeight <= 0f) return ViewfinderBounds.ZERO

    if (aspectRatioPortrait < 0f) {
        return ViewfinderBounds(
            left = 0f,
            top = 0f,
            width = screenWidth,
            height = screenHeight
        )
    }

    if (aspectRatioPortrait == 1.0f || aspectRatioPortrait == 0.75f || aspectRatioPortrait == 0.5625f) {
        if (aspectRatioPortrait == 1.0f && cameraOutputWidth > 0 && cameraOutputHeight > 0) {
            var outputWidth = cameraOutputWidth
            var outputHeight = cameraOutputHeight
            if (displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180) {
                outputWidth = cameraOutputHeight
                outputHeight = cameraOutputWidth
            }

            val cameraBounds = computeCameraImageBounds(
                previewViewWidth = screenWidth,
                previewViewHeight = screenHeight,
                cameraOutputWidth = outputWidth,
                cameraOutputHeight = outputHeight,
                isFullscreen = false
            )

            val squareSize = minOf(cameraBounds.width, cameraBounds.height)
            val squareLeft = cameraBounds.left + (cameraBounds.width - squareSize) / 2f
            val squareTop = cameraBounds.top + (cameraBounds.height - squareSize) / 2f

            return ViewfinderBounds(
                left = squareLeft,
                top = squareTop + offsetYPx,
                width = squareSize,
                height = squareSize
            )
        }

        var viewfinderWidth = screenWidth
        var viewfinderHeight = screenWidth / aspectRatioPortrait
        var viewfinderTop = 0f

        if (viewfinderHeight > screenHeight) {
            viewfinderHeight = screenHeight
            viewfinderWidth = screenHeight * aspectRatioPortrait
            viewfinderTop = offsetYPx
        } else {
            viewfinderTop = (screenHeight - viewfinderHeight) / 2f + offsetYPx
        }

        return ViewfinderBounds(
            left = 0f,
            top = viewfinderTop,
            width = viewfinderWidth,
            height = viewfinderHeight
        )
    }

    if (cameraOutputWidth > 0 && cameraOutputHeight > 0) {
        var outputWidth = cameraOutputWidth
        var outputHeight = cameraOutputHeight
        if (displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180) {
            outputWidth = cameraOutputHeight
            outputHeight = cameraOutputWidth
        }

        val cameraBounds = computeCameraImageBounds(
            previewViewWidth = screenWidth,
            previewViewHeight = screenHeight,
            cameraOutputWidth = outputWidth,
            cameraOutputHeight = outputHeight,
            isFullscreen = aspectRatioPortrait == 0.5625f || aspectRatioPortrait < 0f
        )

        val finalTop = if (aspectRatioPortrait == 0.5625f) {
            screenHeight * 0.18f + offsetYPx
        } else {
            cameraBounds.top + offsetYPx
        }
        return ViewfinderBounds(
            left = cameraBounds.left,
            top = finalTop,
            width = cameraBounds.width,
            height = cameraBounds.height
        )
    }
    val screenRatio = screenWidth / screenHeight
    var viewfinderLeft: Float
    var viewfinderTop: Float
    var viewfinderWidth: Float
    var viewfinderHeight: Float
    if (screenRatio > aspectRatioPortrait) {
        viewfinderHeight = screenHeight
        viewfinderWidth = screenHeight * aspectRatioPortrait
        viewfinderLeft = (screenWidth - viewfinderWidth) / 2f
        viewfinderTop = 0f
    } else {
        viewfinderWidth = screenWidth
        viewfinderHeight = screenWidth / aspectRatioPortrait
        viewfinderTop = (screenHeight - viewfinderHeight) / 2f

        if (aspectRatioPortrait == 0.5625f) {
            viewfinderTop = screenHeight * 0.18f

            val currentBottom = viewfinderTop + viewfinderHeight
            val screenBottom = screenHeight

            if (currentBottom > screenBottom) {
                val excess = currentBottom - screenBottom
                viewfinderHeight -= excess
            }
        }

        viewfinderLeft = 0f
    }
    return ViewfinderBounds(
        left = viewfinderLeft,
        top = viewfinderTop + offsetYPx,
        width = viewfinderWidth,
        height = viewfinderHeight
    )
}
