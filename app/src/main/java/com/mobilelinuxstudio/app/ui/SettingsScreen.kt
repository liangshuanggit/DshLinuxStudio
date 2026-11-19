package com.mobilelinuxstudio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val apiKey by vm.apiKey.collectAsState()
    val dshPort by vm.dshPort.collectAsState()
    val codePort by vm.codePort.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("模型凭证", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = vm::setApiKey,
                    label = { Text("DeepSeek API Key") },
                    placeholder = { Text("sk-...") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "密钥使用 Android Keystore 加密保存，并仅通过进程环境变量注入 dsh；不会写入日志。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("本地端口", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = dshPort,
                    onValueChange = vm::setDshPort,
                    label = { Text("DeepSeek Harness 端口") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = codePort,
                    onValueChange = vm::setCodePort,
                    label = { Text("code-server 端口") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("仅监听 127.0.0.1，不暴露到局域网。", style = MaterialTheme.typography.bodySmall)
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("设备要求", style = MaterialTheme.typography.titleMedium)
                Text("Android 8.0+，arm64-v8a，建议 6GB 以上存储空间。")
                Text("需要联网下载 Ubuntu 根文件系统、Node.js 和 npm 包。")
                Text("不依赖 root，不使用 Termux；PRoot 二进制随 APK 一起分发。")
            }
        }
    }
}
