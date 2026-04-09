package com.aicamera.app.backend.opencv

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * OpenCV 辅助类 - 使用原生 OpenCV 库
 */
object OpenCvHelper {
    private const val TAG = "OpenCvHelper"
    private var isInitialized = false

    fun init(): Boolean {
        if (isInitialized) return true
        isInitialized = OpenCVLoader.initLocal()
        Log.d(TAG, "OpenCV init: $isInitialized")
        return isInitialized
    }

    fun isReady(): Boolean = isInitialized

    /**
     * Bitmap 转 Mat
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    /**
     * Mat 转 Bitmap
     */
    fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    /**
     * 灰度化
     */
    fun toGray(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        return gray
    }

    /**
     * 边缘检测 (Canny)
     */
    fun detectEdges(grayMat: Mat, lowThreshold: Double = 50.0, highThreshold: Double = 150.0): Mat {
        val edges = Mat()
        Imgproc.Canny(grayMat, edges, lowThreshold, highThreshold)
        return edges
    }

    /**
     * 检测水平线，返回水平线角度（度数）
     */
    fun detectHorizonAngle(edges: Mat): Float {
        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 100, 100.0, 10.0)

        var totalAngle = 0.0
        var count = 0

        for (i in 0 until lines.rows()) {
            val vec = lines.get(i, 0)
            if (vec != null && vec.size >= 4) {
                val x1 = vec[0]
                val y1 = vec[1]
                val x2 = vec[2]
                val y2 = vec[3]

                val angle = atan2(y2 - y1, x2 - x1) * 180.0 / Math.PI
                val normalizedAngle = when {
                    angle > 150 -> angle - 180
                    angle < -150 -> angle + 180
                    else -> angle
                }
                if (abs(normalizedAngle) < 30) {
                    totalAngle += normalizedAngle
                    count++
                }
            }
        }

        lines.release()
        return if (count > 0) (totalAngle / count).toFloat() else 0f
    }

    /**
     * 检测显著轮廓
     */
    fun detectSignificantContours(edges: Mat, minArea: Double = 1000.0): List<Rect> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val result = contours
            .map { Imgproc.boundingRect(it) }
            .filter { it.area() > minArea }
            .sortedByDescending { it.area() }

        contours.forEach { it.release() }
        hierarchy.release()

        return result
    }

    /**
     * 检测引导线
     */
    fun detectLeadingLines(edges: Mat, imageWidth: Int, imageHeight: Int): List<LeadingLine> {
        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 80, 50.0, 10.0)

        val leadingLines = mutableListOf<LeadingLine>()
        val centerX = imageWidth / 2.0
        val centerY = imageHeight / 2.0

        for (i in 0 until lines.rows()) {
            val vec = lines.get(i, 0) ?: continue
            if (vec.size < 4) continue

            val x1 = vec[0]
            val y1 = vec[1]
            val x2 = vec[2]
            val y2 = vec[3]

            val midX = (x1 + x2) / 2
            val midY = (y1 + y2) / 2
            val distToCenter = sqrt((midX - centerX) * (midX - centerX) + (midY - centerY) * (midY - centerY))
            val angle = atan2(y2 - y1, x2 - x1) * 180.0 / Math.PI

            leadingLines.add(LeadingLine(x1, y1, x2, y2, angle, distToCenter))
        }

        lines.release()
        return leadingLines.sortedBy { it.distanceToCenter }
    }

    /**
     * 检测对称性
     */
    fun detectSymmetry(mat: Mat): Float {
        val gray = toGray(mat)
        val flipped = Mat()
        Core.flip(gray, flipped, 1)

        val diff = Mat()
        Core.absdiff(gray, flipped, diff)

        val meanDiff = Core.mean(diff).`val`[0]
        val similarity = 1.0 - (meanDiff / 255.0)

        gray.release()
        flipped.release()
        diff.release()

        return similarity.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * 计算色彩分布
     */
    fun analyzeColorDistribution(mat: Mat): ColorDistribution {
        val hsv = Mat()
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV)

        val channels = mutableListOf<Mat>()
        Core.split(hsv, channels)

        val hueHist = Mat()
        Imgproc.calcHist(
            listOf(channels[0]),
            MatOfInt(0),
            Mat(),
            hueHist,
            MatOfInt(180),
            MatOfFloat(0f, 180f)
        )

        var colorfulPixels = 0.0
        for (i in 0 until hueHist.rows()) {
            colorfulPixels += hueHist.get(i, 0)[0]
        }
        val totalPixels = mat.rows() * mat.cols()
        val saturation = Core.mean(channels[1]).`val`[0] / 255.0

        channels.forEach { it.release() }
        hsv.release()
        hueHist.release()

        return ColorDistribution(
            colorfulRatio = (colorfulPixels / totalPixels).toFloat(),
            avgSaturation = saturation.toFloat()
        )
    }

    /**
     * 检测前景物体
     */
    fun detectForegroundRegions(edges: Mat, numRegions: Int = 3): List<ForegroundRegion> {
        val height = edges.rows()
        val width = edges.cols()
        val regionHeight = height / numRegions

        val regions = mutableListOf<ForegroundRegion>()
        for (i in 0 until numRegions) {
            val y = i * regionHeight
            val roi = Rect(0, y, width, regionHeight)
            val regionMat = Mat(edges, roi)

            val nonZero = Core.countNonZero(regionMat)
            val density = nonZero.toFloat() / (width * regionHeight)

            regions.add(ForegroundRegion(
                rect = roi,
                edgeDensity = density,
                hasContent = density > 0.05
            ))
            regionMat.release()
        }

        return regions
    }

    /**
     * 释放 Mat 资源
     */
    fun release(vararg mats: Mat) {
        mats.forEach { it.release() }
    }

    // 数据类
    data class LeadingLine(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val angle: Double,
        val distanceToCenter: Double
    ) {
        val startX: Double get() = x1
        val startY: Double get() = y1
        val endX: Double get() = x2
        val endY: Double get() = y2
        fun isHorizontal(): Boolean = abs(angle) < 30 || abs(angle) > 150
        fun isVertical(): Boolean = abs(abs(angle) - 90) < 30
        fun isDiagonal(): Boolean = !isHorizontal() && !isVertical()
    }

    data class ForegroundRegion(
        val rect: Rect,
        val edgeDensity: Float,
        val hasContent: Boolean
    )

    data class ColorDistribution(
        val colorfulRatio: Float,
        val avgSaturation: Float
    )

    data class CompositionMetrics(
        val horizonAngle: Float,
        val symmetryScore: Float,
        val hasLeadingLines: Boolean,
        val leadingLineDirection: String?,
        val foregroundLayers: Int,
        val colorRichness: Float
    )
}
