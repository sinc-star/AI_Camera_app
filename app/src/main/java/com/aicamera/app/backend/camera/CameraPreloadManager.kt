package com.aicamera.app.backend.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 相机预加载管理器
 * 用于在应用启动时后台预加载相机资源，减少相机预览启动时间
 */
object CameraPreloadManager {
    private const val TAG = "CameraPreloadManager"
    
    // 预加载状态
    private val isPreloaded = AtomicBoolean(false)
    private val isPreloading = AtomicBoolean(false)
    
    // 预加载的相机提供者
    private var preloadedCameraProvider: ProcessCameraProvider? = null
    
    /**
     * 预加载相机资源
     * 应该在应用启动时调用，如 Application.onCreate() 或 MainActivity.onCreate()
     */
    fun preload(context: Context) {
        if (isPreloaded.get() || isPreloading.get()) {
            Log.d(TAG, "相机资源已经预加载或正在预加载")
            return
        }
        
        isPreloading.set(true)
        Log.d(TAG, "开始预加载相机资源")
        
        // 在后台线程执行预加载
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 初始化 ProcessCameraProvider
                val provider = ProcessCameraProvider.getInstance(context).get()
                preloadedCameraProvider = provider
                
                isPreloaded.set(true)
                Log.i(TAG, "相机资源预加载成功")
            } catch (e: Exception) {
                Log.e(TAG, "相机资源预加载失败", e)
            } finally {
                isPreloading.set(false)
            }
        }
    }
    
    /**
     * 获取预加载的相机提供者
     * 如果未预加载或预加载失败，会返回 null
     */
    fun getPreloadedCameraProvider(): ProcessCameraProvider? {
        return preloadedCameraProvider
    }
    
    /**
     * 检查是否已预加载
     */
    fun isPreloaded(): Boolean {
        return isPreloaded.get()
    }
    
    /**
     * 释放预加载的资源
     */
    fun release() {
        preloadedCameraProvider?.unbindAll()
        preloadedCameraProvider = null
        isPreloaded.set(false)
        Log.d(TAG, "相机预加载资源已释放")
    }
}
