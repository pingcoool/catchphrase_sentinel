package com.audiokit.catchphrase.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 跨 UI / 服务共享的轻量运行状态（统计数据仍以 Room 为准）。
 */
object MonitorState {
    val running = MutableStateFlow(false)
    val partialText = MutableStateFlow("")
    val amplitude = MutableStateFlow(0f)
    val error = MutableStateFlow<String?>(null)

    val modelReady = MutableStateFlow(false)
    val modelProgress = MutableStateFlow(0f)
    val modelError = MutableStateFlow<String?>(null)
}
