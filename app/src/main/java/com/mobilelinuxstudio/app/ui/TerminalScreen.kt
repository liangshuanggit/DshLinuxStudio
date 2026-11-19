package com.mobilelinuxstudio.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun TerminalScreen(vm: MainViewModel) {
    var command by remember { mutableStateOf("uname -a") }
    val output by vm.terminalOutput.collectAsState()
    val status by vm.status.collectAsState()
    val outputScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Linux 终端", style = MaterialTheme.typography.titleMedium)
                Text(
                    "命令在 Ubuntu PRoot 环境中执行；工作目录映射到 /root/workspace。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("命令") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = status?.linuxInstalled == true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { vm.runTerminalCommand(command) }
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.runTerminalCommand(command) },
                        enabled = status?.linuxInstalled == true && command.isNotBlank()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("执行", modifier = Modifier.padding(start = 6.dp))
                    }
                    IconButton(onClick = { vm.clearTerminal() }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空输出")
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxSize()) {
            Text(
                if (output.isEmpty()) "尚未执行命令。" else output,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(outputScroll)
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}
