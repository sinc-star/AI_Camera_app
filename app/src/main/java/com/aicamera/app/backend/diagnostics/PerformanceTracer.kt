package com.aicamera.app.backend.diagnostics

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能追踪器 — 用于定位 UI 响应延迟瓶颈。
 *
 * 核心概念：
 * - traceId：一次用户交互（从点击到所有处理完成）的唯一标识
 * - eventId：每个追踪事件的唯一标识
 * - parentId：调用链中的父事件 ID（构建调用树）
 * - threadName：记录事件发生的线程名，用于多线程分析
 *
 * 使用方式：
 * 1. UI 点击处调用 traceClick("名称", traceId=(自动生成))
 * 2. 功能入口处调用 traceStart("名称")，出口处调用 traceEnd(eventId)
 * 3. 完成后调用 dump(traceId) 输出汇总日志，通过 adb logcat -s PerfTracer 查看
 */
object PerformanceTracer {

    private const val TAG = "PerfTracer"

    private val events = ConcurrentLinkedQueue<TraceEvent>()
    private val eventIdCounter = AtomicLong(1)
    private val traceIdCounter = AtomicLong(1)

    /** 每个线程维护独立的调用栈，用于构建父子调用关系 */
    private val callStacks = ThreadLocal.withInitial { ArrayDeque<StackEntry>() }

    private data class StackEntry(
        val eventId: Long,
        val name: String,
        val timestamp: Long
    )

    enum class EventType {
        UI_CLICK,
        FUNC_START,
        FUNC_END,
        /** 在新线程/协程中开始执行 */
        THREAD_START,
        /** 线程/协程执行结束 */
        THREAD_END
    }

    data class TraceEvent(
        val traceId: Long,
        val eventId: Long,
        val eventType: EventType,
        val name: String,
        val threadName: String,
        val timestamp: Long,
        val parentId: Long?,
        val input: String?,
        val output: String?
    )

    // ─── 公共 API ─────────────────────────────────────────────────────────────

    /**
     * UI 点击追踪 — 在用户点击 UI 元素处调用。
     * 自动创建新的 traceId。
     *
     * @param name 点击触发的功能或模块名称，如 "CaptureButton", "AspectRatioChange"
     * @param input 点击附带的参数
     * @return traceId
     */
    fun traceClick(name: String, input: String? = null): Long {
        val traceId = traceIdCounter.getAndIncrement()
        val eventId = eventIdCounter.getAndIncrement()
        val threadName = currentThreadLabel()
        val ts = System.currentTimeMillis()

        events.offer(
            TraceEvent(
                traceId = traceId, eventId = eventId, eventType = EventType.UI_CLICK,
                name = name, threadName = threadName, timestamp = ts,
                parentId = null, input = input, output = null
            )
        )
        currentStack().addLast(StackEntry(eventId, name, ts))
        Log.d(TAG, "[#$traceId] UI_CLICK  $name  |  thread=$threadName${input?.let { "  |  $it" } ?: ""}")
        return traceId
    }

    /**
     * 功能开始追踪 — 功能执行前调用。
     *
     * @param name 功能/模块名称
     * @param traceId 关联的 traceId（通常从 traceClick 获取）
     * @param input 功能的输入参数
     * @return eventId，用于对应的 traceEnd 调用
     */
    fun traceStart(name: String, traceId: Long, input: String? = null): Long {
        val eventId = eventIdCounter.getAndIncrement()
        val threadName = currentThreadLabel()
        val ts = System.currentTimeMillis()
        val stack = currentStack()
        val parentId = stack.lastOrNull()?.eventId

        events.offer(
            TraceEvent(
                traceId = traceId, eventId = eventId, eventType = EventType.FUNC_START,
                name = name, threadName = threadName, timestamp = ts,
                parentId = parentId, input = input, output = null
            )
        )
        stack.addLast(StackEntry(eventId, name, ts))
        return eventId
    }

    /**
     * 功能结束追踪 — 功能执行结束后调用。
     *
     * @param eventId traceStart 返回的 eventId
     * @param output 功能的输出结果（可选）
     */
    fun traceEnd(eventId: Long, output: String? = null) {
        val threadName = currentThreadLabel()
        val ts = System.currentTimeMillis()
        val stack = currentStack()
        val entry = stack.lastOrNull { it.eventId == eventId }
        if (entry != null) stack.remove(entry)

        val traceId = events.firstOrNull { it.eventId == eventId }?.traceId ?: 0L
        val endEventId = eventIdCounter.getAndIncrement()

        events.offer(
            TraceEvent(
                traceId = traceId, eventId = endEventId, eventType = EventType.FUNC_END,
                name = entry?.name ?: "unknown", threadName = threadName, timestamp = ts,
                parentId = eventId, input = null, output = output
            )
        )
    }

    /**
     * 在子协程/子线程入口处调用，记录线程切换。
     *
     * @param name 新线程执行的模块名称
     * @param traceId 父 traceId
     * @param input 输入参数
     * @return eventId
     */
    fun traceThreadStart(name: String, traceId: Long, input: String? = null): Long {
        val eventId = eventIdCounter.getAndIncrement()
        val threadName = currentThreadLabel()
        val ts = System.currentTimeMillis()
        val stack = currentStack()
        val parentId = stack.lastOrNull()?.eventId

        events.offer(
            TraceEvent(
                traceId = traceId, eventId = eventId, eventType = EventType.THREAD_START,
                name = name, threadName = threadName, timestamp = ts,
                parentId = parentId, input = input, output = null
            )
        )
        stack.addLast(StackEntry(eventId, name, ts))
        Log.d(TAG, "[#$traceId] THREAD_START  $name  |  thread=$threadName")
        return eventId
    }

    /**
     * 子协程/子线程执行结束。
     */
    fun traceThreadEnd(eventId: Long, output: String? = null) {
        val threadName = currentThreadLabel()
        val ts = System.currentTimeMillis()
        val stack = currentStack()
        val entry = stack.lastOrNull { it.eventId == eventId }
        if (entry != null) stack.remove(entry)

        val traceId = events.firstOrNull { it.eventId == eventId }?.traceId ?: 0L

        events.offer(
            TraceEvent(
                traceId = traceId, eventId = eventIdCounter.getAndIncrement(),
                eventType = EventType.THREAD_END,
                name = entry?.name ?: "unknown", threadName = threadName, timestamp = ts,
                parentId = eventId, input = null, output = output
            )
        )
    }

    // ─── 汇总输出 ─────────────────────────────────────────────────────────────

    /**
     * 将指定 traceId 的所有事件按时间排序后输出到 logcat。
     * 查看方式：adb logcat -s PerfTracer
     *
     * @param traceId 要汇总的 traceId，如果为 null 则输出所有 trace
     * @param minDurationMs 只输出总耗时超过此阈值的 trace，0 = 全部输出
     */
    fun dump(traceId: Long? = null, minDurationMs: Long = 0) {
        val filtered = if (traceId != null) {
            events.filter { it.traceId == traceId }
        } else {
            events.toList()
        }

        if (filtered.isEmpty()) {
            Log.d(TAG, "[dump] 无事件记录")
            return
        }

        val sorted = filtered.sortedBy { it.timestamp }
        val baseTs = sorted.firstOrNull()?.timestamp ?: 0L
        val grouped = sorted.groupBy { it.traceId }

        for ((tid, traceEvents) in grouped) {
            val clickEvent = traceEvents.firstOrNull { it.eventType == EventType.UI_CLICK }
            val lastEvent = traceEvents.lastOrNull()
            val totalDuration = if (clickEvent != null && lastEvent != null) {
                lastEvent.timestamp - clickEvent.timestamp
            } else 0L

            if (totalDuration < minDurationMs) continue

            Log.i(TAG, "")
            Log.i(TAG, "═══════════════════════════════════════════════════════════")
            Log.i(TAG, "  TRACE #$tid  |  总耗时: ${totalDuration}ms  |  事件数: ${traceEvents.size}")
            if (clickEvent != null) {
                Log.i(TAG, "  触发: ${clickEvent.name}  @ +0ms")
            }
            Log.i(TAG, "───────────────────────────────────────────────────────────")

            // 构建调用深度映射
            val depthMap = mutableMapOf<Long, Int>()
            for (evt in traceEvents) {
                val depth = if (evt.parentId != null) {
                    (depthMap[evt.parentId] ?: -1) + 1
                } else 0
                depthMap[evt.eventId] = depth.coerceAtMost(8)
            }

            for (evt in traceEvents) {
                val depth = depthMap[evt.eventId] ?: 0
                val indent = "  ".repeat(depth)
                val offset = evt.timestamp - baseTs
                val icon = when (evt.eventType) {
                    EventType.UI_CLICK -> "CLICK"
                    EventType.FUNC_START -> "START"
                    EventType.FUNC_END -> "END  "
                    EventType.THREAD_START -> "THR+"
                    EventType.THREAD_END -> "THR-"
                }
                val thread = evt.threadName

                val extra = when (evt.eventType) {
                    EventType.UI_CLICK -> evt.input?.let { " | $it" } ?: ""
                    EventType.FUNC_START -> evt.input?.let { " | $it" } ?: ""
                    EventType.FUNC_END -> evt.output?.let { " | $it" } ?: ""
                    EventType.THREAD_START -> evt.input?.let { " | $it" } ?: ""
                    EventType.THREAD_END -> evt.output?.let { " | $it" } ?: ""
                }

                Log.i(TAG, "$indent$icon  +${String.format("%6d", offset)}ms  [$thread]  ${evt.name}$extra")
            }

            // 耗时排名
            val durations = mutableListOf<Triple<String, Long, Long>>()
            val startMap = mutableMapOf<Long, Pair<String, Long>>()
            for (evt in traceEvents) {
                if (evt.eventType == EventType.FUNC_START || evt.eventType == EventType.THREAD_START) {
                    startMap[evt.eventId] = evt.name to evt.timestamp
                }
                if (evt.eventType == EventType.FUNC_END || evt.eventType == EventType.THREAD_END) {
                    val pid = evt.parentId ?: continue
                    val start = startMap[pid] ?: continue
                    durations.add(Triple(start.first, start.second, evt.timestamp - start.second))
                }
            }

            if (durations.isNotEmpty()) {
                Log.i(TAG, "───────────────────────────────────────────────────────────")
                Log.i(TAG, "  耗时排名 (Top ${minOf(5, durations.size)}):")
                durations
                    .sortedByDescending { it.third }
                    .take(5)
                    .forEachIndexed { idx, (name, _, duration) ->
                        Log.i(TAG, "    ${idx + 1}. $name  =  ${duration}ms")
                    }
            }

            Log.i(TAG, "═══════════════════════════════════════════════════════════")
        }
    }

    /**
     * 清除所有已记录事件（释放内存）。
     */
    fun clear() {
        events.clear()
        callStacks.remove()
    }

    /** 当前事件数 */
    fun eventCount(): Int = events.size

    // ─── 内部工具 ─────────────────────────────────────────────────────────────

    private fun currentStack(): ArrayDeque<StackEntry> {
        var stack = callStacks.get()
        if (stack == null) {
            stack = ArrayDeque()
            callStacks.set(stack)
        }
        return stack
    }

    @Suppress("deprecation")
    private fun currentThreadLabel(): String {
        val t = Thread.currentThread()
        return "${t.name}#${t.id}"
    }
}
