package com.mobilelinuxstudio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobilelinuxstudio.app.runtime.AppState

@Composable
fun HomeScreen(vm: MainViewModel, onOpenWeb: (WebTarget) -> Unit) {
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val logs by vm.logs.collectAsState()
    val dshUrl by vm.dshUrl.collectAsState()
    val codeUrl by vm.codeUrl.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dsh Linux Studio", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "手机上的本地 Linux 开发环境：Ubuntu 24.04 arm64 + Node.js + DeepSeek Harness",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("环境状态", style = MaterialTheme.typography.titleMedium)
                    StatusRow("Linux 根文件系统", status?.linuxInstalled == true, "Ubuntu 24.04.5 arm64")
                    StatusRow("Node.js 运行时", status?.nodeInstalled == true, "v24.22.3")
                    StatusRow("DeepSeek Harness", status?.dshInstalled == true, "@deepseek-ai/dsh")
                    StatusRow("代码工作台", status?.codeServerInstalled == true, "code-server (可选)")
                    HorizontalDivider()
                    Text("工作目录：${status?.workspacePath ?: "准备中"}", style = MaterialTheme.typography.bodySmall)
                    Text("根文件系统：${status?.rootfsPath ?: "准备中"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("第 1 功能：DeepSeek Harness", style = MaterialTheme.typography.titleMedium)
                    ActionButton(
                        text = "1. 安装 Linux 环境",
                        icon = Icons.Default.CloudDownload,
                        enabled = status?.linuxInstalled != true && !busy,
                        onClick = vm::installLinux
                    )
                    ActionButton(
                        text = "2. 安装 DeepSeek Harness",
                        icon = Icons.Default.CloudDownload,
                        enabled = status?.linuxInstalled == true && status?.dshInstalled != true && !busy,
                        onClick = vm::installDsh
                    )
                    ActionButton(
                        text = if (status?.dshRunning == true) "停止 DSH 服务" else "启动 DSH 服务",
                        icon = if (status?.dshRunning == true) Icons.Default.Stop else Icons.Default.PlayArrow,
                        enabled = status?.dshInstalled == true && !busy,
                        onClick = if (status?.dshRunning == true) vm::stopDsh else vm::startDsh
                    )
                    ActionButton(
                        text = "打开 DSH 工作台 ($dshUrl)",
                        icon = Icons.Default.Computer,
                        enabled = status?.dshRunning == true,
                        onClick = { onOpenWeb(WebTarget.DSH) }
                    )
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("本地代码开发", style = MaterialTheme.typography.titleMedium)
                    ActionButton(
                        text = if (status?.codeServerInstalled == true) "code-server 已安装" else "安装 code-server",
                        icon = Icons.Default.Code,
                        enabled = status?.linuxInstalled == true && status?.codeServerInstalled != true && !busy,
                        onClick = vm::installCodeServer
                    )
                    ActionButton(
                        text = if (status?.codeServerRunning == true) "停止代码工作台" else "启动代码工作台",
                        icon = if (status?.codeServerRunning == true) Icons.Default.Stop else Icons.Default.PlayArrow,
                        enabled = status?.codeServerInstalled == true && !busy,
                        onClick = if (status?.codeServerRunning == true) vm::stopCodeServer else vm::startCodeServer
                    )
                    ActionButton(
                        text = "打开代码工作台 ($codeUrl)",
                        icon = Icons.Default.Computer,
                        enabled = status?.codeServerRunning == true,
                        onClick = { onOpenWeb(WebTarget.CODE_SERVER) }
                    )
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("运行日志", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { AppState.clearLogs() }) { Text("清空") }
                    }
                    val recent = logs.takeLast(12).asReversed()
                    if (recent.isEmpty()) {
                        Text("暂无日志。先安装 Linux 环境。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (line in recent) {
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, installed: Boolean, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (installed) "●" else "○",
            color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null)
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}
