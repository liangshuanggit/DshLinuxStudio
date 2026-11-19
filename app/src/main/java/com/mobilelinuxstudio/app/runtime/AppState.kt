package com.mobilelinuxstudio.app.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RuntimeStatus(
    val linuxInstalled: Boolean,
    val nodeInstalled: Boolean,
    val dshInstalled: Boolean,
    val codeServerInstalled: Boolean,
    val dshRunning: Boolean,
    val codeServerRunning: Boolean,
    val rootfsPath: String,
    val workspacePath: String
)

object AppState {
    private val _status = MutableStateFlow<RuntimeStatus?>(null)
    val status: StateFlow<RuntimeStatus?> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _dshUrl = MutableStateFlow("http://127.0.0.1:3080")
    val dshUrl: StateFlow<String> = _dshUrl.asStateFlow()

    // dsh 启动时会打印一次性登录链接（带 token）。这里暂存它，供 WebView 首次认证使用。
    private val _dshAuthUrl = MutableStateFlow("")
    val dshAuthUrl: StateFlow<String> = _dshAuthUrl.asStateFlow()

    private val _codeUrl = MutableStateFlow("http://127.0.0.1:8080")
    val codeUrl: StateFlow<String> = _codeUrl.asStateFlow()

    fun log(message: String) {
        val line = message.trimEnd()
        if (line.isBlank()) return
        _logs.update { (it + line).takeLast(400) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun setBusy(value: Boolean) {
        _busy.value = value
    }

    fun updateStatus(status: RuntimeStatus) {
        _status.value = status
    }

    fun setDshUrl(url: String) {
        _dshUrl.value = url
    }

    fun setDshAuthUrl(url: String) {
        _dshAuthUrl.value = url
    }

    fun clearDshAuthUrl() {
        _dshAuthUrl.value = ""
    }

    fun setCodeUrl(url: String) {
        _codeUrl.value = url
    }
}
