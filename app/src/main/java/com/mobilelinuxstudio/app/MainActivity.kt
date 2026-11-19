package com.mobilelinuxstudio.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilelinuxstudio.app.ui.HomeScreen
import com.mobilelinuxstudio.app.ui.MainViewModel
import com.mobilelinuxstudio.app.ui.SettingsScreen
import com.mobilelinuxstudio.app.ui.TerminalScreen
import com.mobilelinuxstudio.app.ui.WebTarget
import com.mobilelinuxstudio.app.ui.WebViewScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppShell(vm)
                }
            }
        }
    }

    private enum class Tab {
        HOME, TERMINAL, SETTINGS
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun AppShell(vm: MainViewModel) {
        var selectedTab by remember { mutableStateOf(Tab.HOME) }
        var webTarget by remember { mutableStateOf<WebTarget?>(null) }
        val busy by vm.busy.collectAsStateWithLifecycle()

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            while (isActive) {
                vm.refresh()
                delay(1500)
            }
        }

        if (webTarget != null) {
            BackHandler { webTarget = null }
            val target = webTarget ?: WebTarget.DSH
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(if (target == WebTarget.DSH) "DeepSeek Harness" else "代码工作台")
                        }
                    )
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    WebViewScreen(target = target, vm = vm)
                }
            }
            return
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dsh Linux Studio${if (busy) " · 运行中" else ""}") }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == Tab.HOME,
                        onClick = { selectedTab = Tab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("工作台") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.TERMINAL,
                        onClick = { selectedTab = Tab.TERMINAL },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        label = { Text("终端") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Tab.SETTINGS,
                        onClick = { selectedTab = Tab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (selectedTab) {
                    Tab.HOME -> HomeScreen(vm = vm, onOpenWeb = { webTarget = it })
                    Tab.TERMINAL -> TerminalScreen(vm = vm)
                    Tab.SETTINGS -> SettingsScreen(vm = vm)
                }
            }
        }

        LaunchedEffect(webTarget) {
            if (webTarget != null) {
                // 提供系统返回键关闭网页页面的逻辑由 BackHandler 在下方处理。
            }
        }
    }
}
