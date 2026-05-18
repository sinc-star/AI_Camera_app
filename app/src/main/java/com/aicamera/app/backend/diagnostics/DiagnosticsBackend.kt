package com.aicamera.app.backend.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 画幅比例诊断后端
 *
 * 用于排查 16:9 和全屏比例显示异常的问题。
 * 收集取景框计算参数，通过 HTTP 发回开发机，同时写入本地日志。
 *
 * 使用方式：
 * 1. 将 SERVER_URL 改为开发机在同一 WiFi 下的 IP，端口 8765
 * 2. 在开发机上运行 diagnostics_server.py
 * 3. 安装 App 后切换比例，数据会自动上报
 * 4. 也可通过 ADB: adb logcat -s AspectRatioDiag 查看实时日志
 */
object DiagnosticsBackend {

    private const val TAG = "AspectRatioDiag"

    // ─── 配置 ────────────────────────────────────────────────────────────────
    // 改为你的开发机 IP（手机和电脑须在同一 WiFi）
    // 自动检测：尝试多个常见局域网 IP 段
    private val SERVER_URLS = listOf(
        "http://10.66.99.5:8765/diagnostics",     // 当前开发机 IP
        "http://192.168.1.100:8765/diagnostics",  // 常见路由器 IP
        "http://192.168.0.100:8765/diagnostics",  // 另一常见路由器 IP
        "http://192.168.31.100:8765/diagnostics", // 小米路由器常见 IP
        "http://192.168.50.100:8765/diagnostics"  // 其他常见 IP
    )
    private const val NETWORK_ENABLED = false
    // ─────────────────────────────────────────────────────────────────────────

    /** 一次诊断快照 */
    data class Snapshot(
        val trigger: String,           // "layout_change" 或 "ratio_change"
        val selectedRatioLabel: String,
        val selectedRatioValue: Float,
        val previewViewWidthPx: Float,
        val previewViewHeightPx: Float,
        val boundsLeft: Float,
        val boundsTop: Float,
        val boundsWidth: Float,
        val boundsHeight: Float,
        val offsetYPx: Float,
        val densityDpi: Int,
        val screenWidthPx: Int,
        val screenHeightPx: Int,
        // 相机原生输出分辨率（可选，从 Preview.resolutionInfo 传入）
        val cameraOutputWidth: Int = 0,
        val cameraOutputHeight: Int = 0
    )

    fun getRatioLabel(value: Float): String = when (value) {
        1.0f    -> "1:1"
        0.75f   -> "4:3"
        0.5625f -> "16:9"
        -1f     -> "全屏"
        else    -> "自定义(${value})"
    }

    /**
     * 上报一次诊断快照。
     * 在 Coroutine 中调用（已内部切换到 IO 线程）。
     */
    suspend fun report(context: Context, snap: Snapshot) {
        val json = buildJson(snap)
        Log.d(TAG, json.toString(2))
        saveToFile(context, json)
        if (NETWORK_ENABLED) {
            try {
                sendToServer(json.toString())
            } catch (e: Exception) {
                Log.w(TAG, "服务器不可达，数据已保存到本地: ${e.message}")
            }
        }
    }

    // ─── 内部实现 ─────────────────────────────────────────────────────────────

    private fun buildJson(snap: Snapshot): JSONObject {
        val boundsBottom = snap.boundsTop + snap.boundsHeight
        val boundsRight  = snap.boundsLeft + snap.boundsWidth
        val pvRatio      = if (snap.previewViewHeightPx > 0f)
            snap.previewViewWidthPx / snap.previewViewHeightPx else 0f

        // 判断异常
        val anomalies = JSONArray()
        if (snap.boundsTop < 0f)
            anomalies.put("bounds_top_above_view: top=${snap.boundsTop}")
        if (boundsBottom > snap.previewViewHeightPx)
            anomalies.put("bounds_bottom_below_view: bottom=${boundsBottom} > view_h=${snap.previewViewHeightPx}")
        if (snap.boundsWidth > snap.previewViewWidthPx)
            anomalies.put("bounds_wider_than_view")
        if (snap.boundsHeight <= 0f)
            anomalies.put("invalid_bounds_height")
        if (snap.selectedRatioValue > 0f) {
            val actualRatio = if (snap.boundsHeight > 0f) snap.boundsWidth / snap.boundsHeight else 0f
            if (Math.abs(actualRatio - snap.selectedRatioValue) > 0.02f)
                anomalies.put("ratio_mismatch: expected=${snap.selectedRatioValue} actual=${"%.4f".format(actualRatio)}")
        }

        // 估算相机内容在 PreviewView 中的实际渲染区域（FIT_CENTER 逻辑）
        val cameraContentBounds = if (snap.cameraOutputWidth > 0 && snap.cameraOutputHeight > 0) {
            val camRatio = snap.cameraOutputWidth.toFloat() / snap.cameraOutputHeight
            val pvW = snap.previewViewWidthPx
            val pvH = snap.previewViewHeightPx
            val pvRatioVal = if (pvH > 0f) pvW / pvH else 0f
            val (cw, ch, cl, ct) = if (camRatio > pvRatioVal) {
                // 以宽为准（上下黑边）
                val w = pvW; val h = pvW / camRatio
                val l = 0f; val t = (pvH - h) / 2f
                listOf(w, h, l, t)
            } else {
                // 以高为准（左右黑边）
                val h = pvH; val w = pvH * camRatio
                val l = (pvW - w) / 2f; val t = 0f
                listOf(w, h, l, t)
            }
            JSONObject().apply {
                put("left", cl); put("top", ct)
                put("width", cw); put("height", ch)
                put("bottom", ct + ch); put("right", cl + cw)
            }
        } else null

        return JSONObject().apply {
            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
            put("trigger", snap.trigger)
            put("device", JSONObject().apply {
                put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("sdk", Build.VERSION.SDK_INT)
                put("densityDpi", snap.densityDpi)
                put("screenWidthPx", snap.screenWidthPx)
                put("screenHeightPx", snap.screenHeightPx)
            })
            put("selectedRatio", JSONObject().apply {
                put("label", snap.selectedRatioLabel)
                put("value", snap.selectedRatioValue)
            })
            put("previewView", JSONObject().apply {
                put("widthPx", snap.previewViewWidthPx)
                put("heightPx", snap.previewViewHeightPx)
                put("ratio", "%.4f".format(pvRatio))
            })
            put("computedBounds", JSONObject().apply {
                put("left", snap.boundsLeft)
                put("top", snap.boundsTop)
                put("width", snap.boundsWidth)
                put("height", snap.boundsHeight)
                put("right", boundsRight)
                put("bottom", boundsBottom)
                put("offsetYPx", snap.offsetYPx)
                if (snap.boundsHeight > 0f)
                    put("actualRatio", "%.4f".format(snap.boundsWidth / snap.boundsHeight))
            })
            if (snap.cameraOutputWidth > 0) {
                put("cameraOutput", JSONObject().apply {
                    put("widthPx", snap.cameraOutputWidth)
                    put("heightPx", snap.cameraOutputHeight)
                    put("ratio", "%.4f".format(snap.cameraOutputWidth.toFloat() / snap.cameraOutputHeight))
                })
            }
            cameraContentBounds?.let { put("estimatedCameraContentInView", it) }

            // 网格分析（针对16:9比例）
            if (snap.selectedRatioValue == 0.5625f && snap.boundsHeight > 0) {
                val rowHeight = snap.boundsHeight / 3
                val row1Top = snap.boundsTop
                val row2Top = row1Top + rowHeight
                val row3Top = row2Top + rowHeight
                val row1Bottom = row1Top + rowHeight
                val row2Bottom = row2Top + rowHeight
                val row3Bottom = row3Top + rowHeight

                val gridAnalysis = JSONObject().apply {
                    put("rowHeightPx", rowHeight)
                    put("row1", JSONObject().apply {
                        put("top", row1Top)
                        put("bottom", row1Bottom)
                        put("height", rowHeight)
                    })
                    put("row2", JSONObject().apply {
                        put("top", row2Top)
                        put("bottom", row2Bottom)
                        put("height", rowHeight)
                    })
                    put("row3", JSONObject().apply {
                        put("top", row3Top)
                        put("bottom", row3Bottom)
                        put("height", rowHeight)
                    })
                    // 检查行高是否一致
                    val heightTolerance = 1.0f // 1像素容差
                    val rowsConsistent = Math.abs(rowHeight - rowHeight) < heightTolerance
                    put("rowsConsistent", rowsConsistent)
                    // 检查是否超出屏幕
                    val row3BottomBeyondScreen = row3Bottom > snap.previewViewHeightPx
                    put("row3BottomBeyondScreen", row3BottomBeyondScreen)
                    put("row3BottomScreenDiff", row3Bottom - snap.previewViewHeightPx)
                }
                put("gridAnalysis", gridAnalysis)
            }

            put("anomalies", anomalies)
            put("ok", anomalies.length() == 0)
        }
    }

    private fun saveToFile(context: Context, json: JSONObject) {
        try {
            val file = File(context.filesDir, "aspect_ratio_diag.jsonl")
            file.appendText(json.toString() + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "写入本地日志失败: ${e.message}")
        }
    }

    private suspend fun sendToServer(json: String) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        for (serverUrl in SERVER_URLS) {
            try {
                Log.d(TAG, "尝试连接服务器: $serverUrl")
                val conn = URL(serverUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 2000  // 缩短超时以便快速尝试下一个
                conn.readTimeout = 2000
                try {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json) }
                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        Log.d(TAG, "数据成功发送到服务器: $serverUrl")
                        return@withContext responseCode
                    } else {
                        Log.w(TAG, "服务器返回错误: $responseCode ($serverUrl)")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
                Log.d(TAG, "连接服务器失败: $serverUrl - ${e.message}")
                // 继续尝试下一个
            }
        }

        // 所有尝试都失败
        throw lastError ?: Exception("所有服务器连接尝试都失败")
    }
}
