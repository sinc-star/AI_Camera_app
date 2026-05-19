package com.aicamera.app.backend.color

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.aicamera.app.backend.models.ColorAdjustmentParams
import ai.onnxruntime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

/**
 * MobileNetV2 ONNX 调色模型推理类
 *
 * 模型输入: 224x224 BGR 图像 (OpenCV 格式)
 * 模型输出: 5 个调色参数 (exposure, contrast, saturation, highlight, shadow)
 *
 * 使用说明:
 * 1. 将 model_5param_epoch_19.onnx 放入 app/src/main/assets/models/ 目录
 * 2. 在 Application 或 MainActivity 中调用 OnnxColorModel.initialize(context)
 * 3. 使用 analyzeImage(bitmap) 获取调色参数
 */
object OnnxColorModel {

    private const val TAG = "OnnxColorModel"
    private const val MODEL_NAME = "mobilenetv2_color.onnx" 
    private const val INPUT_SIZE = 224 // MobileNetV2 标准输入尺寸
    private const val OUTPUT_SIZE = 5  // 5 个调色参数

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false

    // 输入/输出节点名称
    private var inputName: String = "input"
    private var outputName: String = "output"

    // 归一化参数 (与 Python 脚本一致)
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /**
     * 初始化 ONNX 模型
     * 应在 Application.onCreate() 或 MainActivity.onCreate() 中调用
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            // 创建 ONNX Runtime 环境
            ortEnvironment = OrtEnvironment.getEnvironment()

            // 从 assets 复制模型到缓存目录
            val modelFile = copyModelFromAssets(context)

            // 创建会话选项，启用内存优化
            val sessionOptions = OrtSession.SessionOptions().apply {
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                setIntraOpNumThreads(2)
            }

            // 创建推理会话
            ortSession = ortEnvironment?.createSession(modelFile.absolutePath, sessionOptions)

            // 获取输入输出节点名称
            ortSession?.let { session ->
                val inputInfo = session.inputInfo
                val outputInfo = session.outputInfo

                if (inputInfo.isNotEmpty()) {
                    inputName = inputInfo.keys.first()
                    Log.d(TAG, "Input name: $inputName, shape: ${inputInfo[inputName]?.info}")
                }

                if (outputInfo.isNotEmpty()) {
                    outputName = outputInfo.keys.first()
                    Log.d(TAG, "Output name: $outputName, shape: ${outputInfo[outputName]?.info}")
                }
            }

            isInitialized = true
            Log.i(TAG, "ONNX model initialized successfully with memory optimization")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX model", e)
            false
        }
    }

    /**
     * 分析图像并返回调色参数
     */
    suspend fun analyzeImage(bitmap: Bitmap): ColorAdjustmentParams = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            Log.w(TAG, "Model not initialized, returning default params")
            return@withContext ColorAdjustmentParams(0f, 0f, 1f, 0f, 0f, 0.5f, 0.5f)
        }

        try {
            // 预处理图像
            val inputTensor = preprocessImage(bitmap)

            // 运行推理
            val outputs = ortSession?.run(mapOf(inputName to inputTensor))

            // 解析输出
            val params = parseOutput(outputs)

            // 释放资源
            inputTensor.close()

            params
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            ColorAdjustmentParams(0f, 0f, 1f, 0f, 0f, 0.5f, 0.5f)
        }
    }

    /**
     * 预处理图像 - 与 Python 脚本一致
     * 1. 缩放至 224x224
     * 2. 转换为 BGR 格式 (OpenCV 风格)
     * 3. 归一化到 [0, 1]
     * 4. 标准化 (mean, std)
     * 5. 转换为 NCHW 格式
     */
    private fun preprocessImage(bitmap: Bitmap): OnnxTensor {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaledBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // 直接创建 floatBuffer，避免中间数组，减少内存使用
        val floatBuffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)

        for (i in pixels.indices) {
            val pixel = pixels[i]

            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            // 按 BGR 顺序直接写入 floatBuffer，减少中间数组
            floatBuffer.put((b - MEAN[0]) / STD[0]) // B 通道
        }
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            floatBuffer.put((g - MEAN[1]) / STD[1]) // G 通道
        }
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            floatBuffer.put((r - MEAN[2]) / STD[2]) // R 通道
        }

        floatBuffer.rewind()

        val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        
        // 释放临时 bitmap 资源
        scaledBitmap.recycle()
        
        return OnnxTensor.createTensor(ortEnvironment, floatBuffer, inputShape)
    }

    /**
     * 解析模型输出
     * 输出: [exposure, contrast, saturation, highlight, shadow]
     */
    private fun parseOutput(outputs: OrtSession.Result?): ColorAdjustmentParams {
        if (outputs == null) {
            return ColorAdjustmentParams(0f, 0f, 1f, 0f, 0f, 0.5f, 0.5f)
        }

        try {
            val outputTensor = outputs.get(0) as? OnnxTensor
            val floatBuffer = outputTensor?.floatBuffer
            
            // 正确读取 floatBuffer 数据
            val floatArray = FloatArray(OUTPUT_SIZE)
            if (floatBuffer != null) {
                floatBuffer.rewind()
                floatBuffer.get(floatArray)
            }

            // 根据 Python 脚本的输出格式解析
            // output_dim == 5: exposure, contrast, saturation, highlight, shadow
            // 重要：对参数进行范围限制，防止极端值导致图像异常
            val rawExposure = floatArray.getOrElse(0) { 0f }
            val rawContrast = floatArray.getOrElse(1) { 1f }
            val rawSaturation = floatArray.getOrElse(2) { 1f }
            val rawHighlight = floatArray.getOrElse(3) { 0.5f }
            val rawShadow = floatArray.getOrElse(4) { 0.5f }
            
            // 全局强度控制 — 去掉了HDR曲线(Reinhard/ACES)，可放宽到50%
            // 曝光单独保守(35%)，因为曝光最影响观感
            val INTENSITY = 0.5f
            val EXPOSURE_INTENSITY = 0.35f
            val exposure = (rawExposure * EXPOSURE_INTENSITY).coerceIn(-0.3f, 0.3f)
            val contrast = (1.0f + (rawContrast - 1.0f) * INTENSITY).coerceIn(0.85f, 1.15f)
            val saturation = (1.0f + (rawSaturation - 1.0f) * INTENSITY).coerceIn(0.85f, 1.15f)
            val highlight = (0.5f + (rawHighlight - 0.5f) * INTENSITY).coerceIn(0.0f, 0.7f)
            val shadow = (0.5f + (rawShadow - 0.5f) * INTENSITY).coerceIn(0.0f, 0.7f)

            Log.d(TAG, "========================================")
            Log.d(TAG, "MODEL OUTPUT (原始):")
            Log.d(TAG, "  rawExposure=$rawExposure")
            Log.d(TAG, "  rawContrast=$rawContrast")
            Log.d(TAG, "  rawSaturation=$rawSaturation")
            Log.d(TAG, "  rawHighlight=$rawHighlight")
            Log.d(TAG, "  rawShadow=$rawShadow")
            Log.d(TAG, "MODEL OUTPUT (限制后):")
            Log.d(TAG, "  exposure=%.4f".format(exposure))
            Log.d(TAG, "  contrast=%.4f".format(contrast))
            Log.d(TAG, "  saturation=%.4f".format(saturation))
            Log.d(TAG, "  highlight=%.4f".format(highlight))
            Log.d(TAG, "  shadow=%.4f".format(shadow))
            Log.d(TAG, "========================================")

            return ColorAdjustmentParams(
                exposure = exposure,
                contrast = contrast,
                saturation = saturation,
                sharpness = 0f,  // 模型不输出锐化参数
                temperature = 0f, // 模型不输出色温参数
                highlights = highlight,
                shadows = shadow
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse output", e)
            return ColorAdjustmentParams(0f, 0f, 1f, 0f, 0f, 0.5f, 0.5f)
        }
    }

    /**
     * 从 assets 复制模型到缓存目录
     * 注意：模型内部硬编码了原始数据文件名，需要同时复制两个名称的数据文件
     */
    private fun copyModelFromAssets(context: Context): File {
        val modelFile = File(context.cacheDir, MODEL_NAME)
        
        // 数据文件名（assets 中的名称）
        val assetDataFileName = "$MODEL_NAME.data"
        // 原始文件名（模型内部硬编码的）
        val originalDataFileName = "model_5param_epoch_19.onnx.data"

        // 复制主模型文件
        if (!modelFile.exists()) {
            context.assets.open("models/$MODEL_NAME").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Model copied to: ${modelFile.absolutePath}")
        }

        // 复制数据文件（从 assets 的新名称）
        val dataFile = File(context.cacheDir, assetDataFileName)
        try {
            if (!dataFile.exists()) {
                context.assets.open("models/$assetDataFileName").use { input ->
                    FileOutputStream(dataFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Model data copied to: ${dataFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy data file: $assetDataFileName", e)
        }

        // 同时创建原始名称的副本（模型内部硬编码需要）
        val originalDataFile = File(context.cacheDir, originalDataFileName)
        try {
            if (!originalDataFile.exists() && dataFile.exists()) {
                dataFile.copyTo(originalDataFile)
                Log.d(TAG, "Model data copied to original name: ${originalDataFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy data to original name", e)
        }

        return modelFile
    }

    /**
     * 释放资源
     */
    fun close() {
        ortSession?.close()
        ortEnvironment?.close()
        isInitialized = false
        Log.i(TAG, "ONNX model released")
    }
}
