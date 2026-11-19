package com.mobilelinuxstudio.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilelinuxstudio.app.runtime.AppState
import com.mobilelinuxstudio.app.runtime.RuntimeProvider
import com.mobilelinuxstudio.app.service.RuntimeService
import com.mobilelinuxstudio.app.util.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = RuntimeProvider.get(application)
    private val prefs = SecurePrefs.get(application)

    private val _apiKey = MutableStateFlow(prefs.apiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _dshPort = MutableStateFlow(prefs.dshPort.toString())
    val dshPort: StateFlow<String> = _dshPort.asStateFlow()

    private val _codePort = MutableStateFlow(prefs.codePort.toString())
    val codePort: StateFlow<String> = _codePort.asStateFlow()

    private val _terminalOutput = MutableStateFlow("")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    val status = AppState.status
    val logs = AppState.logs
    val busy = AppState.busy
    val dshUrl = AppState.dshUrl
    val dshAuthUrl = AppState.dshAuthUrl
    val codeUrl = AppState.codeUrl

    fun refresh() {
        AppState.updateStatus(runtime.status())
    }

    fun setApiKey(value: String) {
        _apiKey.value = value
        prefs.apiKey = value
    }

    fun setDshPort(value: String) {
        _dshPort.value = value
        value.toIntOrNull()?.let { prefs.dshPort = it }
    }

    fun setCodePort(value: String) {
        _codePort.value = value
        value.toIntOrNull()?.let { prefs.codePort = it }
    }

    fun installLinux() {
        RuntimeService.start(getApplication(), RuntimeService.ACTION_INSTALL_LINUX)
    }

    fun installDsh() {
        RuntimeService.start(getApplication(), RuntimeService.ACTION_INSTALL_DSH)
    }

    fun installCodeServer() {
        RuntimeService.start(getApplication(), RuntimeService.ACTION_INSTALL_CODE)
    }

    fun startDsh() {
        RuntimeService.start(getApplication(), RuntimeService.ACTION_START_DSH) {
            putExtra(RuntimeService.EXTRA_API_KEY, _apiKey.value.trim())
            putExtra(RuntimeService.EXTRA_PORT, _dshPort.value.toIntOrNull() ?: 3080)
        }
    }

    fun stopDsh() {
        RuntimeService.start(getApplication(), RuntimeService.ACTION_STOP_DSH)
    }

    fun startCodeServer() {
        RuntimeService.start(getApplication(), RuntimeService.ACTION_START_CODE) {
            putExtra(RuntimeService.EXTRA_PORT, _codePort.value.toIntOrNull() ?: 8080)
        }
    }

    fun stopCodeServer() {
        RuntimeService.start(getApplication(), RuntimeService.ACTION_STOP_CODE)
    }

    fun runTerminalCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val line = "\n$ ${command}\n"
            appendTerminal(line)
            try {
                val process = runtime.startTerminal(command)
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { appendTerminal("$it\n") }
                }
                val code = process.waitFor()
                appendTerminal("[exit=$code]\n")
                withContext(Dispatchers.Main) { refresh() }
            } catch (e: Exception) {
                appendTerminal("[error] ${e.message}\n")
            }
        }
    }

    fun clearTerminal() {
        _terminalOutput.value = ""
    }

    private suspend fun appendTerminal(text: String) {
        withContext(Dispatchers.Main) {
            val current = _terminalOutput.value
            _terminalOutput.value = if (current.length + text.length > 20_000) {
                text
            } else {
                current + text
            }
        }
    }
}
