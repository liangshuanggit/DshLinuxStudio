package com.mobilelinuxstudio.app.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mobilelinuxstudio.app.runtime.AppState

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(target: WebTarget, vm: MainViewModel) {
    val dshUrl by vm.dshUrl.collectAsState()
    val dshAuthUrl by vm.dshAuthUrl.collectAsState()
    val codeUrl by vm.codeUrl.collectAsState()
    val url = if (target == WebTarget.DSH) dshUrl else codeUrl

    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = false
                    allowContentAccess = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val requested = request?.url?.host.orEmpty()
                        return requested !in setOf("127.0.0.1", "localhost")
                    }
                }
                webView = this
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    // dsh 的一次性登录链接到达后只加载一次；加载后立即清除，避免重复认证导致 token 失效。
    LaunchedEffect(dshAuthUrl) {
        if (target == WebTarget.DSH && dshAuthUrl.isNotBlank()) {
            webView?.loadUrl(dshAuthUrl)
            AppState.clearDshAuthUrl()
        }
    }
}
