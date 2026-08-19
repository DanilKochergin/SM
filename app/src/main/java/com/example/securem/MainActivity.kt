package com.example.securesm

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Защита: запрет скриншотов и скрытие превью в недавних задачах
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecureContainerScreen(targetUrl = "https://max.ru")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SecureContainerScreen(targetUrl: String) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }

    // Обработка кнопки "Назад"
    BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasError) {
            // Экран сетевой ошибки
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.height(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Ошибка подключения к сети",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    hasError = false
                    webViewInstance?.reload()
                }) {
                    Text("Повторить")
                }
            }
        } else {
            // Изолированный WebView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT

                            // Запрет доступа к локальной файловой системе
                            allowFileAccess = false
                            allowContentAccess = false

                            // 1. Десктопный User-Agent (Chrome на Windows 10/11)
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

                            // 2. Включаем масштабирование и десктопный viewport
                            useWideViewPort = true
                            loadWithOverviewMode = true

                            // Поддержка зума (чтобы можно было приблизить мелкие элементы интерфейса)
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false // Скрывает системные некрасивые кнопки +/-
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                hasError = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                hasError = true
                            }

                            // Блокировка внешних intent-схем
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return !(url.startsWith("http://") || url.startsWith("https://"))
                            }

                            // Блокировка сторонней аналитики
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: ""
                                val trackers = listOf("mc.yandex.ru", "google-analytics.com", "top-fwz1.mail.ru", "vk.com/rtrg")
                                if (trackers.any { url.contains(it) }) {
                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            // Сброс всех запросов к микрофону / камере через браузер
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.deny()
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                            }
                        }

                        loadUrl(targetUrl)
                        webViewInstance = this
                    }
                }
            )
        }

        // Тонкий индикатор загрузки сверху
        AnimatedVisibility(
            visible = loadProgress in 0.01f..0.99f,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
    }
}