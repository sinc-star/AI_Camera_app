package com.aicamera.app.backend.ai

enum class AiModelProvider(
    val displayName: String,
    val modelName: String,
    val apiUrl: String,
    val description: String,
    val helpUrl: String
) {
    ALIBABA_QWEN(
        displayName = "阿里云通义千问",
        modelName = "qwen-vl-plus",
        apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        description = "阿里云通义千问视觉模型，支持图像理解",
        helpUrl = "help.aliyun.com/zh/model-studio"
    ),
    
    ZHIPU_GLM(
        displayName = "智谱AI GLM-4V",
        modelName = "glm-4v",
        apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        description = "智谱AI多模态模型，支持图像理解",
        helpUrl = "open.bigmodel.cn"
    ),
    
    BAIDU_ERNIE(
        displayName = "百度文心一言",
        modelName = "ernie-4.0-vl",
        apiUrl = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-4.0-vl",
        description = "百度文心一言视觉模型，支持图像理解",
        helpUrl = "console.bce.baidu.com/qianfan"
    ),
    
    MOONSHOT(
        displayName = "月之暗面 Moonshot",
        modelName = "moonshot-v1-8k-vision",
        apiUrl = "https://api.moonshot.cn/v1/chat/completions",
        description = "月之暗面视觉模型，支持图像理解",
        helpUrl = "platform.moonshot.cn"
    );

    companion object {
        fun fromName(name: String): AiModelProvider {
            return entries.find { it.name == name } ?: ALIBABA_QWEN
        }
    }
}
