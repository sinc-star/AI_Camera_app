package com.aicamera.app.backend.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object CloudAiService {
    private const val TAG = "CloudAiService"
    private const val PREFS_NAME = "cloud_ai_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL_PROVIDER = "model_provider"

    private var cachedApiKey: String? = null
    private var cachedModelProvider: AiModelProvider? = null

    fun setApiKey(context: Context, apiKey: String) {
        val encryptedPrefs = SecurePrefs.getEncryptedPrefs(context)
        encryptedPrefs.edit().putString(KEY_API_KEY, apiKey).apply()
        cachedApiKey = apiKey
    }

    fun getApiKey(context: Context): String? {
        if (cachedApiKey != null) return cachedApiKey
        val encryptedPrefs = SecurePrefs.getEncryptedPrefs(context)
        cachedApiKey = encryptedPrefs.getString(KEY_API_KEY, null)
        return cachedApiKey
    }

    fun setModelProvider(context: Context, provider: AiModelProvider) {
        val encryptedPrefs = SecurePrefs.getEncryptedPrefs(context)
        encryptedPrefs.edit().putString(KEY_MODEL_PROVIDER, provider.name).apply()
        cachedModelProvider = provider
    }

    fun getModelProvider(context: Context): AiModelProvider {
        if (cachedModelProvider != null) return cachedModelProvider!!
        val encryptedPrefs = SecurePrefs.getEncryptedPrefs(context)
        val providerName = encryptedPrefs.getString(KEY_MODEL_PROVIDER, AiModelProvider.ALIBABA_QWEN.name)
        cachedModelProvider = AiModelProvider.fromName(providerName ?: AiModelProvider.ALIBABA_QWEN.name)
        return cachedModelProvider!!
    }

    fun hasApiKey(context: Context): Boolean {
        return !getApiKey(context).isNullOrBlank()
    }

    fun clearApiKey(context: Context) {
        val encryptedPrefs = SecurePrefs.getEncryptedPrefs(context)
        encryptedPrefs.edit().remove(KEY_API_KEY).apply()
        cachedApiKey = null
    }

    suspend fun analyzeScene(
        context: Context,
        bitmap: Bitmap,
        detectedObjects: List<String>,
        currentSettings: CameraSettingsInfo
    ): CloudAiResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "[云端 AI] API Key 未配置，跳过云端分析")
            return@withContext CloudAiResult(
                success = false,
                suggestions = emptyList(),
                errorMessage = "请先在设置中配置 API Key"
            )
        }

        val provider = getModelProvider(context)
        Log.d(TAG, "[云端 AI] 开始分析场景，模型：${provider.displayName} (${provider.modelName})")
        Log.d(TAG, "[云端 AI] 检测到物体：${detectedObjects.joinToString(", ")}")
        Log.d(TAG, "[云端 AI] 当前设置：ISO=${currentSettings.iso ?: "自动"}, 快门=${currentSettings.shutterSpeed ?: "自动"}, EV=${currentSettings.ev ?: "自动"}")

        try {
            val base64Image = bitmapToBase64(bitmap)
            val prompt = buildPrompt(detectedObjects, currentSettings)
            
            Log.d(TAG, "[云端 AI] 正在调用 API...")
            val response = when (provider) {
                AiModelProvider.ALIBABA_QWEN -> callOpenAICompatibleApi(provider, apiKey, prompt, base64Image)
                AiModelProvider.ZHIPU_GLM -> callOpenAICompatibleApi(provider, apiKey, prompt, base64Image)
                AiModelProvider.BAIDU_ERNIE -> callBaiduErnieApi(provider, apiKey, prompt, base64Image)
                AiModelProvider.MOONSHOT -> callOpenAICompatibleApi(provider, apiKey, prompt, base64Image)
            }
            val result = parseResponse(response, provider)
            
            if (result.success) {
                Log.i(TAG, "[云端 AI] 分析成功，建议：${result.suggestions.joinToString(", ")}")
            } else {
                Log.w(TAG, "[云端 AI] 分析失败：${result.errorMessage}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "[云端 AI] 分析异常：${e.message}", e)
            CloudAiResult(
                success = false,
                suggestions = emptyList(),
                errorMessage = "AI 分析失败：${e.message}"
            )
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        val scaledBitmap = if (bitmap.width > 512 || bitmap.height > 512) {
            val scale = 512f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildPrompt(detectedObjects: List<String>, currentSettings: CameraSettingsInfo): String {
        val objectsStr = if (detectedObjects.isNotEmpty()) {
            "检测到的物体：${detectedObjects.joinToString(", ")}"
        } else {
            "未检测到特定物体"
        }

        return """
你是一位专业摄影师和构图指导专家。请根据照片内容给出专业、具体的构图和拍摄建议。

当前场景信息:
$objectsStr

当前相机设置:
- ISO: ${currentSettings.iso ?: "自动"}
- 快门速度：${currentSettings.shutterSpeed ?: "自动"}
- 曝光补偿：${currentSettings.ev ?: "自动"}

请从以下维度分析照片，给出 2-3 条具体、可执行的专业建议：

【构图指导】
- 三分法：检查主体是否位于三分线交点或线上
- 对称性：建筑、倒影等场景的对称轴是否居中
- 引导线：是否有道路、河流等引导视线的元素
- 前景层次：是否有前景增加画面深度
- 留白处理：画面是否有适当留白，避免拥挤
- 水平线：地平线、海平面是否水平

【光线与色彩】
- 光线方向：顺光、侧光、逆光的运用是否恰当
- 对比度：明暗对比是否突出主体
- 色彩搭配：冷暖色对比、相近色协调

【拍摄参数】
- 曝光：根据直方图判断是否过曝或欠曝
- 景深：虚化程度是否适合当前场景
- 快门：是否需要调整以捕捉动态或长曝光

输出格式要求:
每条建议按以下格式输出:
[类型] 具体建议内容

类型包括：构图、光线、参数、视角

示例:
[构图] 将人物向左移动，放在左侧三分线交点处
[光线] 侧光角度较好，可略微调整角度增强立体感
[参数] ISO 偏高，建议降低到 200 以减少噪点
[视角] 尝试降低机位，以更低角度拍摄

严格要求:
1. 建议必须具体可执行，避免空泛描述
2. 使用专业术语但保持易懂
3. 针对检测到的物体和场景给出个性化建议
4. 不要只说"调高/降低"，要说明调整到什么程度或为了什么效果
5. 避免重复建议，每条建议都应有独立价值

直接输出建议，不要添加开场白、总结或其他多余内容。
        """.trimIndent()
    }

    private fun callOpenAICompatibleApi(
        provider: AiModelProvider,
        apiKey: String,
        prompt: String,
        base64Image: String
    ): String {
        val url = URL(provider.apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            val requestBody = JSONObject().apply {
                put("model", provider.modelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 500)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("API 请求失败 ($responseCode): $errorStream")
            }

            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun callBaiduErnieApi(
        provider: AiModelProvider,
        apiKey: String,
        prompt: String,
        base64Image: String
    ): String {
        val url = URL("${provider.apiUrl}?access_token=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            val requestBody = JSONObject().apply {
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image")
                                put("image", base64Image)
                            })
                        })
                    })
                })
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("API 请求失败 ($responseCode): $errorStream")
            }

            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(response: String, provider: AiModelProvider): CloudAiResult {
        val json = JSONObject(response)
        
        val content = when (provider) {
            AiModelProvider.BAIDU_ERNIE -> {
                json.optString("result", "").trim()
            }
            else -> {
                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return CloudAiResult(
                        success = false,
                        suggestions = emptyList(),
                        errorMessage = "API 返回数据格式错误"
                    )
                }
                choices.getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "")
                    .trim()
            }
        }

        if (content.isBlank()) {
            return CloudAiResult(
                success = false,
                suggestions = emptyList(),
                errorMessage = "AI 未返回有效建议"
            )
        }

        val suggestions = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(5)

        return CloudAiResult(
            success = true,
            suggestions = suggestions,
            errorMessage = null
        )
    }
}

data class CameraSettingsInfo(
    val iso: Int? = null,
    val shutterSpeed: String? = null,
    val ev: Int? = null
)

data class CloudAiResult(
    val success: Boolean,
    val suggestions: List<String>,
    val errorMessage: String?
)
