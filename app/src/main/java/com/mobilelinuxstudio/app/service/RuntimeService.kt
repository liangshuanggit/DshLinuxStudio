package com.mobilelinuxstudio.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mobilelinuxstudio.app.MainActivity
import com.mobilelinuxstudio.app.R
import com.mobilelinuxstudio.app.runtime.AppState
import com.mobilelinuxstudio.app.runtime.RuntimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuntimeService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val runtime by lazy { RuntimeProvider.get(this) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Linux 环境服务已就绪"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: ACTION_NOOP
        when (action) {
            ACTION_INSTALL_LINUX -> installLinux()
            ACTION_INSTALL_DSH -> installDsh()
            ACTION_INSTALL_CODE -> installCodeServer()
            ACTION_START_DSH -> {
                val key = intent?.getStringExtra(EXTRA_API_KEY).orEmpty()
                val port = intent?.getIntExtra(EXTRA_PORT, 3080) ?: 3080
                // 补装 xdg-user-dirs 要跑 apt（耗时），放后台线程做，再启动 dsh。
                scope.launch {
                    AppState.setBusy(true)
                    try {
                        val repaired = runtime.ensureXdgUserDir()
                        if (repaired.exitCode != 0) {
                            AppState.log("[dsh] 补装 xdg-user-dirs 失败，默认工作区可能仍无法创建")
                        }
                        val result = runtime.startDsh(key, port)
                        AppState.log(result.output)
                        monitor(runtime.dshProcessOrNull(), "dsh")
                        refreshStatus()
                    } finally {
                        AppState.setBusy(false)
                    }
                }
            }
            ACTION_STOP_DSH -> {
                runtime.stopDsh()
                AppState.log("[dsh] 已停止")
                refreshStatus()
                maybeStopService()
            }
            ACTION_START_CODE -> {
                val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
                val result = runtime.startCodeServer(port)
                AppState.log(result.output)
                monitor(runtime.codeProcessOrNull(), "code-server")
                refreshStatus()
            }
            ACTION_STOP_CODE -> {
                runtime.stopCodeServer()
                AppState.log("[code-server] 已停止")
                refreshStatus()
                maybeStopService()
            }
            ACTION_RUN_COMMAND -> {
                val command = intent?.getStringExtra(EXTRA_COMMAND).orEmpty()
                if (command.isNotBlank()) {
                    monitor(runtime.startTerminal(command), "terminal")
                }
            }
        }
        return START_STICKY
    }

    private fun installLinux() {
        scope.launch {
            AppState.setBusy(true)
            updateNotification("正在安装 Linux 环境")
            val result = withContext(Dispatchers.IO) {
                runtime.installLinux { message, progress ->
                    AppState.log("[Linux] $message")
                    updateNotification(message, progress)
                }
            }
            AppState.log("[Linux] 安装结果：exit=${result.exitCode}")
            if (result.exitCode != 0) AppState.log(result.output.takeLast(1500))
            AppState.setBusy(false)
            refreshStatus()
            updateNotification("Linux 环境安装结束")
        }
    }

    private fun installDsh() {
        scope.launch {
            AppState.setBusy(true)
            updateNotification("正在安装 DeepSeek Harness")
            val result = withContext(Dispatchers.IO) {
                runtime.installDsh { message, progress ->
                    AppState.log("[DSH] $message")
                    updateNotification(message, progress)
                }
            }
            AppState.log("[DSH] 安装结果：exit=${result.exitCode}")
            AppState.log(result.output.takeLast(1500))
            AppState.setBusy(false)
            refreshStatus()
            updateNotification("DeepSeek Harness 安装结束")
        }
    }

    private fun installCodeServer() {
        scope.launch {
            AppState.setBusy(true)
            updateNotification("正在安装 code-server")
            val result = withContext(Dispatchers.IO) {
                runtime.installCodeServer { message, progress ->
                    AppState.log("[Code] $message")
                    updateNotification(message, progress)
                }
            }
            AppState.log("[Code] 安装结果：exit=${result.exitCode}")
            if (result.exitCode != 0) AppState.log(result.output.takeLast(1500))
            AppState.setBusy(false)
            refreshStatus()
            updateNotification("code-server 安装结束")
        }
    }

    private fun monitor(process: Process?, name: String) {
        if (process == null) return
        scope.launch(Dispatchers.IO) {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line -> handleProcessLine(name, line) }
            }
            val code = runCatching { process.exitValue() }.getOrDefault(-1)
            AppState.log("[$name] 进程退出，exit=$code")
            withContext(Dispatchers.Main) {
                refreshStatus()
                maybeStopService()
            }
        }
    }

    private fun handleProcessLine(name: String, rawLine: String) {
        if (name == "dsh") captureDshAuthUrl(rawLine)
        AppState.log("[$name] ${redact(rawLine)}")
    }

    private fun captureDshAuthUrl(rawLine: String) {
        val clean = ANSI_REGEX.replace(rawLine, "")
        val match = DS_TOKEN_URL_REGEX.find(clean) ?: return
        val url = match.value.trimEnd(')', ']', '}', '.', ',', ';', '"', '\'')
        if (url == AppState.dshAuthUrl.value) return
        AppState.setDshAuthUrl(url)
        AppState.setDshUrl(url.substringBefore('?'))
        AppState.log("[dsh] 已捕获 Web 登录链接，将在工作台中自动打开。")
    }

    private fun redact(rawLine: String): String {
        var line = ANSI_REGEX.replace(rawLine, "")
        line = TOKEN_VALUE_REGEX.replace(line) { match -> match.groupValues[1] + "***" }
        line = API_KEY_REGEX.replace(line, "sk-***")
        return line
    }

    private fun refreshStatus() {
        AppState.updateStatus(runtime.status())
    }

    private fun maybeStopService() {
        val status = runtime.status()
        if (!status.dshRunning && !status.codeServerRunning && !AppState.busy.value) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Linux 运行环境",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Ubuntu 环境、DeepSeek Harness 与代码服务运行状态" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_service)
            .setContentTitle("Dsh Linux Studio")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String, progress: Int? = null) {
        val notification = buildNotification(text).apply {
            if (progress != null) {
                // 通过 extras 设置进度条，避免重建 Builder 的重复代码。
                extras.putInt(NotificationCompat.EXTRA_PROGRESS, progress)
                extras.putInt(NotificationCompat.EXTRA_PROGRESS_MAX, 100)
                extras.putBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE, false)
            }
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "linux_runtime"
        private const val NOTIFICATION_ID = 2026

        const val EXTRA_ACTION = "action"
        const val EXTRA_API_KEY = "api_key"
        const val EXTRA_PORT = "port"
        const val EXTRA_COMMAND = "command"

        const val ACTION_INSTALL_LINUX = "install_linux"
        const val ACTION_INSTALL_DSH = "install_dsh"
        const val ACTION_INSTALL_CODE = "install_code"
        const val ACTION_START_DSH = "start_dsh"
        const val ACTION_STOP_DSH = "stop_dsh"
        const val ACTION_START_CODE = "start_code"
        const val ACTION_STOP_CODE = "stop_code"
        const val ACTION_RUN_COMMAND = "run_command"
        private const val ACTION_NOOP = "noop"

        private val ANSI_REGEX = Regex("\\u001B\\[[;?0-9]*[A-Za-z]")
        private val DS_TOKEN_URL_REGEX =
            Regex("https?://[^\\s\"'<>]+[?&]token=[^\\s\"'<>()\\[\\]{},;]+")
        private val TOKEN_VALUE_REGEX =
            Regex("(?i)(token=)[^\\s\"'<>()\\[\\]{},;]+")
        private val API_KEY_REGEX = Regex("sk-[A-Za-z0-9_\\-]{8,}")

        fun start(context: Context, action: String, block: (Intent.() -> Unit)? = null) {
            val intent = Intent(context, RuntimeService::class.java).apply {
                putExtra(EXTRA_ACTION, action)
                block?.invoke(this)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
